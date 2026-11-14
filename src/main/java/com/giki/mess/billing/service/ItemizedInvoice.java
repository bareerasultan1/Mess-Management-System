package com.giki.mess.billing.service;

import com.giki.mess.billing.model.BillItem;
import com.giki.mess.billing.model.MealType;
import com.giki.mess.common.Money;
import java.util.List;
import java.util.Map;

/**
 * Read-only breakdown shown to the student so they can see HOW the bill was calculated
 * (the "no transparency" problem in the project proposal).
 */
public record ItemizedInvoice(
        String billId,
        String studentId,
        int month,
        int year,
        int daysAttended,
        Map<MealType, Integer> mealsByType,
        Money baseAmount,
        Money rebateAmount,
        Money discountAmount,
        Money fineAmount,
        Money netPayable,
        List<BillItem> lines) {
}
