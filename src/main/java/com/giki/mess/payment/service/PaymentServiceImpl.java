package com.giki.mess.payment.service;

import com.giki.mess.common.Money;
import com.giki.mess.payment.factory.PaymentProcessorFactory;
import com.giki.mess.payment.model.PaymentCallback;
import com.giki.mess.payment.model.PaymentChannel;
import com.giki.mess.payment.model.PaymentResponse;
import com.giki.mess.payment.model.PaymentTransaction;
import com.giki.mess.repository.IBillRepository;
import com.giki.mess.repository.ILedgerRepository;
import com.giki.mess.repository.IPaymentRepository;

public class PaymentServiceImpl implements PaymentService {

    private final PaymentProcessorFactory factory;
    private final IPaymentRepository payments;
    private final IBillRepository bills;
    private final ILedgerRepository ledgers;

    public PaymentServiceImpl(PaymentProcessorFactory factory, IPaymentRepository payments,
                              IBillRepository bills, ILedgerRepository ledgers) {
        this.factory = factory;
        this.payments = payments;
        this.bills = bills;
        this.ledgers = ledgers;
    }

    @Override
    public PaymentResponse initiatePayment(String studentId, String billId, Money amount,
                                           PaymentChannel channel, String idempotencyKey) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }

    @Override
    public PaymentTransaction settlePayment(PaymentCallback callback) {
        throw new UnsupportedOperationException("not implemented yet (TDD RED)");
    }
}
