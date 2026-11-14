package com.giki.mess.billing.strategy;

import com.giki.mess.billing.config.TariffConfiguration;
import com.giki.mess.billing.model.StudentCategory;
import java.util.EnumMap;
import java.util.Map;

/** Builds the category-to-strategy table used by the billing service. */
public final class BillingStrategies {

    private BillingStrategies() {
    }

    public static Map<StudentCategory, BillingCalculationStrategy> defaults(TariffConfiguration tariff) {
        Map<StudentCategory, BillingCalculationStrategy> map = new EnumMap<>(StudentCategory.class);
        BillingCalculationStrategy standard = new StandardBillingStrategy(tariff);
        for (StudentCategory c : StudentCategory.values()) {
            map.put(c, standard);
        }
        return map;
    }
}
