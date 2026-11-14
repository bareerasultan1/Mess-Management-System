package com.giki.mess.payment.gateway;

import com.giki.mess.payment.model.PaymentChannel;
import java.time.Clock;

/**
 * SIMULATED bank (1Link / IBAN) transfer confirmation. In addition to the signature, a bank
 * confirmation must carry a bank reference number so the transfer can be audited.
 */
public class BankTransferProcessor extends AbstractSignedProcessor {

    public BankTransferProcessor(String secret, Clock clock) {
        super(PaymentChannel.BANK_TRANSFER, secret, clock);
    }
}
