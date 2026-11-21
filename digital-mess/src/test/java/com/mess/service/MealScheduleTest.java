package com.mess.service;

import com.mess.domain.MealType;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealScheduleTest {

    private final MealSchedule schedule = MealSchedule.defaults();

    @Test
    void windowStartIsInclusiveAndEndIsExclusive() {
        assertEquals(Optional.empty(), schedule.mealAt(LocalTime.of(6, 59, 59)));
        assertEquals(Optional.of(MealType.BREAKFAST), schedule.mealAt(LocalTime.of(7, 0)));
        assertEquals(Optional.of(MealType.BREAKFAST), schedule.mealAt(LocalTime.of(9, 29, 59)));
        assertEquals(Optional.empty(), schedule.mealAt(LocalTime.of(9, 30)));
    }

    @Test
    void lunchAndDinnerAreRecognised() {
        assertEquals(Optional.of(MealType.LUNCH), schedule.mealAt(LocalTime.of(13, 0)));
        assertEquals(Optional.of(MealType.DINNER), schedule.mealAt(LocalTime.of(19, 0)));
        assertEquals(Optional.empty(), schedule.mealAt(LocalTime.of(21, 30)));
    }

    @Test
    void timeBetweenMealsHasNoMeal() {
        assertEquals(Optional.empty(), schedule.mealAt(LocalTime.of(16, 0)));
        assertEquals(Optional.empty(), schedule.mealAt(LocalTime.MIDNIGHT));
    }
}
