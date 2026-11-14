package com.giki.mess.billing.strategy;

import com.giki.mess.billing.model.MealType;
import com.giki.mess.common.Money;

/**
 * STRATEGY PATTERN: how a student category is charged.
 * The billing service only knows this interface, so a new category is added
 * with a new class and no change to BillingServiceImpl (Open/Closed Principle).
 */
public interface BillingCalculationStrategy {

    /** Price charged to this kind of student for one meal. */
    Money rateFor(MealType mealType);

    /** Discount granted on the net meal cost (base minus rebate). */
    Money discountOn(Money netMealCost);
}
