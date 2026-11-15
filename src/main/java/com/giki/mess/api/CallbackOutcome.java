package com.giki.mess.api;

/** What happened to a gateway callback; DUPLICATE_ACKNOWLEDGED is a success for the gateway. */
public enum CallbackOutcome {
    SETTLED,
    PAYMENT_FAILED,
    DUPLICATE_ACKNOWLEDGED
}
