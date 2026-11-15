package com.giki.mess.api;

import com.giki.mess.common.InvalidInputException;
import com.giki.mess.common.Money;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Boundary validation: nothing reaches the services unless it passes here.
 * Whitelist style (accept only the known-good shape) which also stops injection-style input.
 * The limits are the equivalence-class boundaries used by the SQA black-box tests.
 */
public class BillingInputValidator {

    private static final Pattern STUDENT_ID = Pattern.compile("^20\\d{5}$");
    private static final Pattern BILL_ID = Pattern.compile("^BILL-20\\d{5}-\\d{4}-(1[0-2]|[1-9])$");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final BigDecimal MAX_AMOUNT =
        BigDecimal.valueOf(PaymentTransaction.MAX_AMOUNT.paisa(), 2);

    public String studentId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (!STUDENT_ID.matcher(id).matches()) {
            throw new InvalidInputException("studentId",
                "Student ID must be 7 digits starting with 20, for example 2023294.");
        }
        return id;
    }

    /** Accepts 0.01 up to 50,000.00 PKR with at most two decimal places. */
    public Money paymentAmount(BigDecimal raw) {
        if (raw == null) {
            throw new InvalidInputException("amount", "Payment amount is required.");
        }
        if (raw.signum() <= 0) {
            throw new InvalidInputException("amount", "Payment amount must be greater than zero.");
        }
        if (raw.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("amount", "Payment amount can have at most 2 decimal places.");
        }
        if (raw.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidInputException("amount",
                "A single payment cannot exceed " + PaymentTransaction.MAX_AMOUNT + ".");
        }
        return Money.ofPaisa(raw.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact());
    }

    public int month(int raw) {
        if (raw < 1 || raw > 12) {
            throw new InvalidInputException("month", "Month must be between 1 and 12.");
        }
        return raw;
    }

    public int year(int raw) {
        if (raw < 2020 || raw > 2100) {
            throw new InvalidInputException("year", "Year must be between 2020 and 2100.");
        }
        return raw;
    }

    public String billId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (!BILL_ID.matcher(id).matches()) {
            throw new InvalidInputException("billId", "Bill number is not in the expected format.");
        }
        return id;
    }

    public String idempotencyKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidInputException("idempotencyKey",
                "Payment reference must be 1-64 letters, digits, dots, dashes or underscores.");
        }
        return key;
    }

    public PaymentChannel channel(String raw) {
        String allowed = Arrays.toString(PaymentChannel.values());
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("channel", "Payment method is required: " + allowed + ".");
        }
        try {
            return PaymentChannel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("channel", "Payment method must be one of " + allowed + ".");
        }
    }

    public LocalDate asOfDate(LocalDate raw) {
        if (raw == null) {
            throw new InvalidInputException("asOfDate", "A date is required.");
        }
        return raw;
    }
}
