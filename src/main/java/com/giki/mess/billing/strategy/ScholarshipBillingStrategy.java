package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;

/** Approved scholarship students: standard rates, 25% off the net meal cost. */
public class ScholarshipBillingStrategy extends PercentageRateStrategy {

    public ScholarshipBillingStrategy(TariffConfiguration tariff) {
        super(tariff, 100, 25);
    }
}
