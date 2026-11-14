package com.giki.mess.payment.gateway;

import com.giki.mess.payment.model.PaymentChannel;
import java.time.Clock;

/** SIMULATED EasyPaisa mobile-account sandbox. Callbacks are signed with HMAC-SHA256. */
public class EasyPaisaProcessor extends AbstractSignedProcessor {

    public EasyPaisaProcessor(String secret, Clock clock) {
        super(PaymentChannel.EASYPAISA, secret, clock);
    }
}
