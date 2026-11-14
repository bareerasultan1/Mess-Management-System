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
    private final int absenceThresholdDays;
    private final int rebatePercent;
    private final int maxRebateDays;

    public TariffConfiguration(Map<MealType, Money> mealRates, int dueDayOfNextMonth,
                               int absenceThresholdDays, int rebatePercent, int maxRebateDays) {
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
        if (absenceThresholdDays < 1 || maxRebateDays < 0
                || rebatePercent < 0 || rebatePercent > 100) {
            throw new com.giki.mess.billing.exception.InvalidTariffException(
                "Invalid rebate rule: threshold>=1, 0<=percent<=100, maxDays>=0");
        }
        this.absenceThresholdDays = absenceThresholdDays;
        this.rebatePercent = rebatePercent;
        this.maxRebateDays = maxRebateDays;
        this.mealRates = new EnumMap<>(mealRates);
        this.dueDayOfNextMonth = dueDayOfNextMonth;
    }

    /** GIKI mess defaults: breakfast 100, lunch 150, dinner 150 PKR; bills due on the 10th. */
    public static TariffConfiguration defaults() {
        Map<MealType, Money> rates = new EnumMap<>(MealType.class);
        rates.put(MealType.BREAKFAST, Money.pkr(100));
        rates.put(MealType.LUNCH, Money.pkr(150));
        rates.put(MealType.DINNER, Money.pkr(150));
        return new TariffConfiguration(rates, 10, 3, 80, 20);
    }

    public Money rateFor(MealType type) {
        return mealRates.get(type);
    }

    /** Sum of all meal rates = what one full day costs. */
    public Money dailyRate() {
        Money total = Money.ZERO;
        for (Money rate : mealRates.values()) {
            total = total.plus(rate);
        }
        return total;
    }

    /** Minimum consecutive registered absence days before a rebate is given (default 3). */
    public int absenceThresholdDays() {
        return absenceThresholdDays;
    }

    /** Percent of the daily rate credited for each qualifying absence day (default 80). */
    public int rebatePercent() {
        return rebatePercent;
    }

    /** Upper limit on rebated days per bill (default 20). */
    public int maxRebateDays() {
        return maxRebateDays;
    }

    public int dueDayOfNextMonth() {
        return dueDayOfNextMonth;
    }
}
