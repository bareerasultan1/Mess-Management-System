package com.giki.mess.api;

import com.giki.mess.billing.exception.BillNotFoundException;
import com.giki.mess.billing.exception.BillingException;
import com.giki.mess.billing.exception.LedgerNotFoundException;
import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.ItemizedInvoice;
import com.giki.mess.common.InvalidInputException;
import com.giki.mess.payment.exception.DuplicateTransactionException;
import com.giki.mess.payment.exception.GatewayTimeoutException;
import com.giki.mess.payment.exception.InvalidCallbackException;
import com.giki.mess.payment.exception.PaymentException;
import com.giki.mess.payment.exception.UnsupportedPaymentChannelException;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.payment.model.TransactionStatus;
import com.giki.mess.payment.service.PaymentService;
import com.giki.mess.repository.PersistenceException;
import com.giki.mess.util.LogRedactor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The module boundary of Billing and Payment (what a UI or web layer would call).
 *
 * <p>Responsibilities:
 *  1. validate every input (BillingInputValidator) before any service runs,
 *  2. convert every domain exception into a structured, user-friendly ApiResult, so callers never
 *     see stack traces or internal class names,
 *  3. write an audit trail ("com.giki.mess.audit" logger, logs/billing_audit.log) in which student
 *     ids are masked.
 */
public class BillingPaymentApi {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.giki.mess.audit");
    private static final Logger LOG = LoggerFactory.getLogger(BillingPaymentApi.class);

    private final BillingService billing;
    private final PaymentService payments;
    private final BillingInputValidator validator;

    /**
     * Creates the boundary over the billing and payment services.
     */
    public BillingPaymentApi(BillingService billing, PaymentService payments,
                             BillingInputValidator validator) {
        this.billing = billing;
        this.payments = payments;
        this.validator = validator;
    }

    /**
     * Validates the input, then calculates the student's bill for the month.
     */
    public ApiResult<Bill> generateMonthlyBill(String studentId, int month, int year, LocalDate asOf) {
        return run("GENERATE_BILL", studentId, () -> billing.calculateMonthlyBill(
            validator.studentId(studentId), validator.month(month), validator.year(year),
            validator.asOfDate(asOf)));
    }

    public ApiResult<ItemizedInvoice> itemizedInvoice(String billId) {
        return run("ITEMIZED_INVOICE", null,
            () -> billing.generateItemizedInvoice(validator.billId(billId)));
    }

    /**
     * Validates the input, then starts an online payment for a bill.
     */
    public ApiResult<PaymentResponse> startPayment(String studentId, String billId,
                                                   BigDecimal amountPkr, String channel,
                                                   String idempotencyKey) {
        return run("START_PAYMENT", studentId, () -> payments.initiatePayment(
            validator.studentId(studentId), validator.billId(billId),
            validator.paymentAmount(amountPkr), validator.channel(channel),
            validator.idempotencyKey(idempotencyKey)));
    }

    /** Gateway webhook entry point. A duplicate callback is acknowledged as a success. */
    public ApiResult<CallbackOutcome> handleGatewayCallback(PaymentCallback callback) {
        if (callback == null) {
            return fail("GATEWAY_CALLBACK", null, new InvalidCallbackException("Callback is missing"));
        }
        return run("GATEWAY_CALLBACK", callback.studentId(), () -> {
            try {
                PaymentTransaction tx = payments.settlePayment(callback);
                return tx.getStatus() == TransactionStatus.FAILED
                    ? CallbackOutcome.PAYMENT_FAILED : CallbackOutcome.SETTLED;
            } catch (DuplicateTransactionException e) {
                return CallbackOutcome.DUPLICATE_ACKNOWLEDGED;
            }
        });
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    private <T> ApiResult<T> run(String action, String studentId, Supplier<T> work) {
        try {
            T result = work.get();
            AUDIT.info("{} result={} student={}", action,
                result instanceof CallbackOutcome o ? o.name() : "OK", LogRedactor.maskStudentId(studentId));
            return ApiResult.ok(result);
        } catch (RuntimeException e) {
            return fail(action, studentId, e);
        }
    }

    private <T> ApiResult<T> fail(String action, String studentId, RuntimeException e) {
        ErrorInfo info = toErrorInfo(e);
        AUDIT.warn("{} result={} student={}", action, info.code(), LogRedactor.maskStudentId(studentId));
        if ("INTERNAL_ERROR".equals(info.code()) || "STORAGE_ERROR".equals(info.code())) {
            LOG.error("Unexpected failure during {}", action, e);        // details stay in the log
        } else {
            LOG.warn("{} rejected: {} ({})", action, info.code(), e.getClass().getSimpleName());
        }
        return new ApiResult<>(false, null, info);
    }

    /** Maps an exception to a user-safe error: input and billing problems, then payment, then system faults. */
    private static ErrorInfo toErrorInfo(RuntimeException e) {
        ErrorInfo info = describeInputOrBillingProblem(e);
        if (info == null) {
            info = describePaymentProblem(e);
        }
        return info != null ? info : describeSystemFault(e);
    }

    private static ErrorInfo describeInputOrBillingProblem(RuntimeException e) {
        if (e instanceof InvalidInputException) {
            return new ErrorInfo("INPUT_INVALID", e.getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            return new ErrorInfo("INPUT_INVALID", "The request contains an invalid value.");
        }
        if (e instanceof LedgerNotFoundException) {
            return new ErrorInfo("LEDGER_NOT_FOUND", "No mess account was found for this student.");
        }
        if (e instanceof BillNotFoundException) {
            return new ErrorInfo("BILL_NOT_FOUND", "No bill was found with that number.");
        }
        if (e instanceof BillingException) {
            return new ErrorInfo("BILLING_ERROR", "The bill could not be calculated.");
        }
        return null;
    }

    private static ErrorInfo describePaymentProblem(RuntimeException e) {
        if (e instanceof DuplicateTransactionException) {
            return new ErrorInfo("DUPLICATE_PAYMENT", "This payment was already submitted.");
        }
        if (e instanceof GatewayTimeoutException) {
            return new ErrorInfo("GATEWAY_UNAVAILABLE", "The payment service is not responding. "
                + "You have not been charged. Please try again later.");
        }
        if (e instanceof InvalidCallbackException) {
            return new ErrorInfo("INVALID_CALLBACK", "The payment confirmation was rejected.");
        }
        if (e instanceof UnsupportedPaymentChannelException) {
            return new ErrorInfo("CHANNEL_UNSUPPORTED", "This payment method is not available.");
        }
        if (e instanceof PaymentException p) {
            return switch (p.getErrorCode()) {
                case "BILL_OWNER_MISMATCH" -> new ErrorInfo(p.getErrorCode(),
                    "This bill belongs to another student.");
                case "BILL_ALREADY_PAID" -> new ErrorInfo(p.getErrorCode(), "This bill is already paid.");
                case "AMOUNT_EXCEEDS_DUE" -> new ErrorInfo(p.getErrorCode(),
                    "The amount is more than what is currently owed on this bill.");
                default -> new ErrorInfo("PAYMENT_FAILED", "The payment could not be processed.");
            };
        }
        return null;
    }

    private static ErrorInfo describeSystemFault(RuntimeException e) {
        if (e instanceof PersistenceException) {
            return new ErrorInfo("STORAGE_ERROR",
                "A temporary problem occurred while saving your data. Please try again.");
        }
        return new ErrorInfo("INTERNAL_ERROR",
            "Something went wrong. Please try again or contact the mess office.");
    }
}
