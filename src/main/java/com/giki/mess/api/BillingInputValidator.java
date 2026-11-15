package com.giki.mess.api;

import com.giki.mess.common.Money;
import com.giki.mess.payment.model.PaymentChannel;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Skeleton: behaviour is added test-first. */
public class BillingInputValidator {

    public String studentId(String raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public Money paymentAmount(BigDecimal raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public int month(int raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public int year(int raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public String billId(String raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public String idempotencyKey(String raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public PaymentChannel channel(String raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public LocalDate asOfDate(LocalDate raw) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
