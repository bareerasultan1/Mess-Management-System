package com.giki.mess.billing.service;

import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.billing.model.StudentLedger;
import com.giki.mess.common.Money;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BillingServiceImpl implements BillingService {

    private final ILedgerRepository ledgers;
    private final IBillRepository bills;
    private final AttendanceDataSource attendance;

    public BillingServiceImpl(ILedgerRepository ledgers, IBillRepository bills,
                              AttendanceDataSource attendance) {
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
            Money rate = rateFor(meal.mealType());
            items.add(new BillItem(meal.eventId(), billId, studentId,
                meal.scanTime(), meal.mealType(), rate));
            base = base.plus(rate);
        }

        LocalDate dueDate = LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(10);
        Bill bill = new Bill(billId, studentId, month, year, dueDate,
            base, Money.ZERO, Money.ZERO, Money.ZERO, items);
        return bills.save(bill);
    }

    private Money rateFor(MealType type) {
        return switch (type) {
            case BREAKFAST -> Money.pkr(100);
            case LUNCH, DINNER -> Money.pkr(150);
        };
    }
}
