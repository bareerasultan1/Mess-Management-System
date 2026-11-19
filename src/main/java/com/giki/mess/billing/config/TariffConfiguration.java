package com.giki.mess.billing.config;

import com.giki.mess.billing.exception.InvalidTariffException;
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
    private final int lateFinePercent;
    private final Money administrativeCharge;

    /**
     * Creates a tariff, rejecting missing rates or impossible rule values.
     */
    public TariffConfiguration(Map<MealType, Money> mealRates, int dueDayOfNextMonth,
                               int absenceThresholdDays, int rebatePercent, int maxRebateDays,
                               int lateFinePercent, Money administrativeCharge) {
        if (mealRates == null || mealRates.size() != MealType.values().length) {
            throw new InvalidTariffException("A rate is required for every meal type");
        }
        requireValid(mealRates.values().stream().allMatch(r -> r != null && r.isPositive()),
            "Meal rates must be positive");
        requireValid(dueDayOfNextMonth >= 1 && dueDayOfNextMonth <= 28,
            "Due day must be between 1 and 28, got: " + dueDayOfNextMonth);
        requireValid(absenceThresholdDays >= 1 && maxRebateDays >= 0
                && rebatePercent >= 0 && rebatePercent <= 100,
            "Invalid rebate rule: threshold>=1, 0<=percent<=100, maxDays>=0");
        requireValid(lateFinePercent >= 0 && lateFinePercent <= 100,
            "Late fine percent must be 0-100, got: " + lateFinePercent);
        requireValid(administrativeCharge != null && administrativeCharge.isPositive(),
            "Administrative charge must be positive");
        this.administrativeCharge = administrativeCharge;
        this.lateFinePercent = lateFinePercent;
        this.absenceThresholdDays = absenceThresholdDays;
        this.rebatePercent = rebatePercent;
        this.maxRebateDays = maxRebateDays;
        this.mealRates = new EnumMap<>(mealRates);
        this.dueDayOfNextMonth = dueDayOfNextMonth;
    }


    private static void requireValid(boolean condition, String message) {
        if (!condition) {
            throw new InvalidTariffException(message);
        }
    }

    /** GIKI mess defaults: breakfast 100, lunch 150, dinner 150 PKR; bills due on the 10th. */
    public static TariffConfiguration defaults() {
        Map<MealType, Money> rates = new EnumMap<>(MealType.class);
        rates.put(MealType.BREAKFAST, Money.pkr(100));
        rates.put(MealType.LUNCH, Money.pkr(150));
        rates.put(MealType.DINNER, Money.pkr(150));
        return new TariffConfiguration(rates, 10, 3, 80, 20, 5, Money.pkr(100));
    }

    public Money rateFor(MealType type) {
        return mealRates.get(type);
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

    /** Penalty (percent of the amount due) when a bill is unpaid after its due date (default 5). */
    public int lateFinePercent() {
        return lateFinePercent;
    }

    /** Minimum bill for a month in which the student ate no meals (default 100 PKR). */
    public Money administrativeCharge() {
        return administrativeCharge;
    }

    public int dueDayOfNextMonth() {
        return dueDayOfNextMonth;
    }
}
