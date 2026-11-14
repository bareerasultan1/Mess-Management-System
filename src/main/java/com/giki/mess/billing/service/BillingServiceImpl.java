package com.giki.mess.billing.service;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.exception.InvalidTariffException;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.PaymentStatus;
import com.giki.mess.billing.model.StudentCategory;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.billing.strategy.BillingCalculationStrategy;
import com.giki.mess.common.Money;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class BillingServiceImpl implements BillingService {

    private final ILedgerRepository ledgers;
    private final IBillRepository bills;
    private final AttendanceDataSource attendance;
    private final TariffConfiguration tariff;
    private final Map<StudentCategory, BillingCalculationStrategy> strategies;

    public BillingServiceImpl(ILedgerRepository ledgers, IBillRepository bills,
                              AttendanceDataSource attendance, TariffConfiguration tariff,
                              Map<StudentCategory, BillingCalculationStrategy> strategies) {
        this.tariff = tariff;
        this.strategies = strategies;
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
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate cannot be null");
        }
        StudentLedger ledger = ledgers.findByStudentId(studentId)
            .orElseThrow(() -> new LedgerNotFoundException(studentId));

        BillingCalculationStrategy strategy = strategies.get(ledger.getCategory());
        if (strategy == null) {
            throw new InvalidTariffException("No tariff strategy for category " + ledger.getCategory());
        }

        // A bill that already received a payment must never be recalculated or overwritten.
        Optional<Bill> existing = bills.findByStudentAndMonth(studentId, month, year);
        if (existing.isPresent() && (existing.get().getPaymentStatus() == PaymentStatus.PAID
                || existing.get().getPaymentStatus() == PaymentStatus.PARTIALLY_PAID)) {
            return existing.get();
        }

        String billId = "BILL-" + studentId + "-" + year + "-" + month;
        List<BillItem> items = new ArrayList<>();
        Money base = Money.ZERO;
        List<MealRecord> meals = attendance.mealsFor(studentId, month, year);
        Set<LocalDate> daysWithMeals = new HashSet<>();
        for (MealRecord meal : meals) {
            daysWithMeals.add(meal.scanTime().toLocalDate());
            Money rate = strategy.rateFor(meal.mealType());
            items.add(new BillItem(meal.eventId(), billId, studentId,
                meal.scanTime(), meal.mealType(), rate));
            base = base.plus(rate);
        }

        if (items.isEmpty()) {
            base = tariff.administrativeCharge();   // minimal charge when no meals were eaten
        }

        Money rebate = calculateRebate(
            attendance.registeredAbsences(studentId, month, year), daysWithMeals, month, year, strategy);
        Money mealCostAfterRebate = base.minus(rebate).floorAtZero();
        Money discount = strategy.discountOn(mealCostAfterRebate);

        LocalDate dueDate = LocalDate.of(year, month, 1).plusMonths(1)
            .withDayOfMonth(tariff.dueDayOfNextMonth());
        Money fine = Money.ZERO;
        if (asOfDate != null && asOfDate.isAfter(dueDate)) {
            fine = mealCostAfterRebate.minus(discount).percent(tariff.lateFinePercent());
        }

        Bill bill = new Bill(billId, studentId, month, year, dueDate,
            base, rebate, fine, discount, items);
        return bills.save(bill);
    }

    @Override
    public ItemizedInvoice generateItemizedInvoice(String billId) {
        Bill bill = bills.findById(billId).orElseThrow(() -> new BillNotFoundException(billId));
        Map<MealType, Integer> counts = new EnumMap<>(MealType.class);
        for (MealType type : MealType.values()) {
            counts.put(type, 0);
        }
        Set<LocalDate> days = new HashSet<>();
        for (BillItem item : bill.getItems()) {
            counts.merge(item.getMealType(), 1, Integer::sum);
            days.add(item.getScanTime().toLocalDate());
        }
        return new ItemizedInvoice(bill.getBillId(), bill.getStudentId(), bill.getMonth(),
            bill.getYear(), days.size(), counts, bill.getBaseAmount(), bill.getRebateAmount(),
            bill.getDiscountAmount(), bill.getFineAmount(), bill.getNetPayable(), bill.getItems());
    }

    /**
     * Rebate rule: only runs of at least N consecutive registered absence days count
     * (N = threshold, default 3). Each counted day is credited rebatePercent of the daily
     * rate. The number of counted days is capped (default 20).
     */
    private Money calculateRebate(Set<LocalDate> absences, Set<LocalDate> daysWithMeals,
                                  int month, int year,
                                  BillingCalculationStrategy strategy) {
        TreeSet<LocalDate> days = new TreeSet<>();
        for (LocalDate d : absences) {
            if (d.getMonthValue() == month && d.getYear() == year && !daysWithMeals.contains(d)) {
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
        Money dailyRate = Money.ZERO;
        for (MealType type : MealType.values()) {
            dailyRate = dailyRate.plus(strategy.rateFor(type));
        }
        return dailyRate.percent(tariff.rebatePercent()).times(counted);
    }
}
