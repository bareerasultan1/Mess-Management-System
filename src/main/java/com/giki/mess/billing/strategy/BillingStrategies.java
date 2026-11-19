package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.StudentCategory;
import java.util.EnumMap;
import java.util.Map;

/** Builds the category-to-strategy table used by the billing service. */
public final class BillingStrategies {

    private BillingStrategies() {
    }

    /**
     * Returns the standard, subsidized, scholarship and guest strategies keyed by category.
     */
    public static Map<StudentCategory, BillingCalculationStrategy> defaults(TariffConfiguration tariff) {
        Map<StudentCategory, BillingCalculationStrategy> map = new EnumMap<>(StudentCategory.class);
        map.put(StudentCategory.REGULAR, new StandardBillingStrategy(tariff));
        map.put(StudentCategory.SUBSIDIZED, new SubsidizedBillingStrategy(tariff));
        map.put(StudentCategory.SCHOLAR, new ScholarshipBillingStrategy(tariff));
        map.put(StudentCategory.FACULTY_GUEST, new GuestBillingStrategy(tariff));
        return map;
    }
}
