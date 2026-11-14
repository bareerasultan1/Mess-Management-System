package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;

/** Need-based subsidy: meals cost 60% of the standard rate, no further discount. */
public class SubsidizedBillingStrategy extends PercentageRateStrategy {

    public SubsidizedBillingStrategy(TariffConfiguration tariff) {
        super(tariff, 60, 0);
    }
}
