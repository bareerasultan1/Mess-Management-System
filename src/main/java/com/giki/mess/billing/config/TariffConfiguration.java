package com.giki.mess.billing.config;

import com.giki.mess.billing.model.MealType;
import com.giki.mess.common.Money;
import java.util.EnumMap;
import java.util.Map;

/**
 * All billing numbers in ONE place (no "magic numbers" inside the service).
 * Change a rate here and no calculation code has to be touched.
 */
public final class TariffConfiguration {

    private final Map<MealType, Money> mealRates;
    private final int dueDayOfNextMonth;

    public TariffConfiguration(Map<MealType, Money> mealRates, int dueDayOfNextMonth) {
        if (mealRates == null || mealRates.size() != MealType.values().length) {
            throw new com.giki.mess.billing.exception.InvalidTariffException(
                "A rate is required for every meal type");
        }
        for (Money rate : mealRates.values()) {
            if (rate == null || !rate.isPositive()) {
                throw new com.giki.mess.billing.exception.InvalidTariffException(
                    "Meal rates must be positive");
            }
        }
        if (dueDayOfNextMonth < 1 || dueDayOfNextMonth > 28) {
            throw new com.giki.mess.billing.exception.InvalidTariffException(
                "Due day must be between 1 and 28, got: " + dueDayOfNextMonth);
        }
        this.mealRates = new EnumMap<>(mealRates);
        this.dueDayOfNextMonth = dueDayOfNextMonth;
    }

    /** GIKI mess defaults: breakfast 100, lunch 150, dinner 150 PKR; bills due on the 10th. */
    public static TariffConfiguration defaults() {
        Map<MealType, Money> rates = new EnumMap<>(MealType.class);
        rates.put(MealType.BREAKFAST, Money.pkr(100));
        rates.put(MealType.LUNCH, Money.pkr(150));
        rates.put(MealType.DINNER, Money.pkr(150));
        return new TariffConfiguration(rates, 10);
    }

    public Money rateFor(MealType type) {
        return mealRates.get(type);
    }

    public int dueDayOfNextMonth() {
        return dueDayOfNextMonth;
    }
}
