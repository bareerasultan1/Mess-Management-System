package com.giki.mess.api;

import com.giki.mess.billing.model.Bill;
import com.giki.mess.billing.service.BillingService;
import com.giki.mess.billing.service.ItemizedInvoice;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Skeleton: behaviour is added test-first. */
public class BillingPaymentApi {

    public BillingPaymentApi(BillingService billing, PaymentService payments,
                             BillingInputValidator validator) {
    }

    public ApiResult<Bill> generateMonthlyBill(String studentId, int month, int year, LocalDate asOf) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public ApiResult<ItemizedInvoice> itemizedInvoice(String billId) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public ApiResult<PaymentResponse> startPayment(String studentId, String billId,
                                                   BigDecimal amountPkr, String channel,
                                                   String idempotencyKey) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    public ApiResult<CallbackOutcome> handleGatewayCallback(PaymentCallback callback) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
