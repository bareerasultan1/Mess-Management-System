package com.giki.mess.billing.service;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import java.time.LocalDate;

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
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
