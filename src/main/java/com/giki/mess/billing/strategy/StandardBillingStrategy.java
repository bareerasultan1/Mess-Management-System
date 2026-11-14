package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.common.Money;

/** Regular students: configured rates, no discount. */
public class StandardBillingStrategy implements BillingCalculationStrategy {

    private final TariffConfiguration tariff;

    public StandardBillingStrategy(TariffConfiguration tariff) {
        this.tariff = tariff;
    }

    @Override
    public Money rateFor(MealType mealType) {
        return tariff.rateFor(mealType);
    }

    @Override
    public Money discountOn(Money netMealCost) {
        return Money.ZERO;
    }
}
