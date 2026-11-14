package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.common.Money;

/**
 * Shared implementation for strategies that scale the configured rate by a percentage
 * and optionally give a percentage discount on the net meal cost.
 * Concrete strategies (Subsidized, Scholarship, Guest) are thin named subclasses so the
 * business rule of each category has one obvious home.
 */
abstract class PercentageRateStrategy implements BillingCalculationStrategy {

    private final TariffConfiguration tariff;
    private final int ratePercent;
    private final int discountPercent;

    PercentageRateStrategy(TariffConfiguration tariff, int ratePercent, int discountPercent) {
        this.tariff = tariff;
        this.ratePercent = ratePercent;
        this.discountPercent = discountPercent;
    }

    @Override
    public Money rateFor(MealType mealType) {
        return tariff.rateFor(mealType).percent(ratePercent);
    }

    @Override
    public Money discountOn(Money netMealCost) {
        return netMealCost.percent(discountPercent);
    }
}
