package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;

/** Faculty guests: premium tariff of 120% of the standard rate, no discount. */
public class GuestBillingStrategy extends PercentageRateStrategy {

    public GuestBillingStrategy(TariffConfiguration tariff) {
        super(tariff, 120, 0);
    }
}
