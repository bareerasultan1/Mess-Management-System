package com.giki.mess.payment.gateway;

import com.giki.mess.payment.model.PaymentChannel;
import java.time.Clock;

/** SIMULATED JazzCash mobile-wallet sandbox. Callbacks are signed with HMAC-SHA256. */
public class JazzCashProcessor extends AbstractSignedProcessor {

    public JazzCashProcessor(String secret, Clock clock) {
        super(PaymentChannel.JAZZCASH, secret, clock);
    }
}
