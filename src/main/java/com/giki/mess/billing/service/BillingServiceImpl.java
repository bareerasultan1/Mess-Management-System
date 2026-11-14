package com.giki.mess.billing.service;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class BillingServiceImpl implements BillingService {

    private final ILedgerRepository ledgers;
    private final IBillRepository bills;
    private final AttendanceDataSource attendance;
    private final TariffConfiguration tariff;

    public BillingServiceImpl(ILedgerRepository ledgers, IBillRepository bills,
                              AttendanceDataSource attendance, TariffConfiguration tariff) {
        this.tariff = tariff;
        this.ledgers = ledgers;
        this.bills = bills;
        this.attendance = attendance;
    }

    @Override
    public Bill calculateMonthlyBill(String studentId, int month, int year, LocalDate asOfDate) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId cannot be null or blank");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12, got: " + month);
        }
        StudentLedger ledger = ledgers.findByStudentId(studentId)
            .orElseThrow(() -> new LedgerNotFoundException(studentId));

        String billId = "BILL-" + studentId + "-" + year + "-" + month;
        List<BillItem> items = new ArrayList<>();
        Money base = Money.ZERO;
        for (MealRecord meal : attendance.mealsFor(studentId, month, year)) {
            Money rate = tariff.rateFor(meal.mealType());
            items.add(new BillItem(meal.eventId(), billId, studentId,
                meal.scanTime(), meal.mealType(), rate));
            base = base.plus(rate);
        }

        Money rebate = calculateRebate(
            attendance.registeredAbsences(studentId, month, year), month, year);

        LocalDate dueDate = LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(tariff.dueDayOfNextMonth());
        Bill bill = new Bill(billId, studentId, month, year, dueDate,
            base, rebate, Money.ZERO, Money.ZERO, items);
        return bills.save(bill);
    }

    /**
     * Rebate rule: only runs of at least N consecutive registered absence days count
     * (N = threshold, default 3). Each counted day is credited rebatePercent of the daily
     * rate. The number of counted days is capped (default 20).
     */
    private Money calculateRebate(Set<LocalDate> absences, int month, int year) {
        TreeSet<LocalDate> days = new TreeSet<>();
        for (LocalDate d : absences) {
            if (d.getMonthValue() == month && d.getYear() == year) {
                days.add(d);
            }
        }
        int qualifyingDays = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = (previous != null && previous.plusDays(1).equals(day)) ? run + 1 : 1;
            if (run == tariff.absenceThresholdDays()) {
                qualifyingDays += run;          // the whole run starts counting
            } else if (run > tariff.absenceThresholdDays()) {
                qualifyingDays++;               // each further day in the run
            }
            previous = day;
        }
        int counted = Math.min(qualifyingDays, tariff.maxRebateDays());
        return tariff.dailyRate().percent(tariff.rebatePercent()).times(counted);
    }
}
