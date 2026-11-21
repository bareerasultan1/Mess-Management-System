package com.mess.service;

import com.mess.domain.MealType;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Daily meal windows. Start is inclusive, end is exclusive. Immutable after construction. */
public class MealSchedule {

    public record Window(LocalTime start, LocalTime end) {
        boolean contains(LocalTime t) {
            return !t.isBefore(start) && t.isBefore(end);
        }
    }

    private final Map<MealType, Window> windows;

    public MealSchedule(Map<MealType, Window> windows) {
        this.windows = new EnumMap<>(MealType.class);
        this.windows.putAll(windows);
    }

    public static MealSchedule defaults() {
        return new MealSchedule(Map.of(
                MealType.BREAKFAST, new Window(LocalTime.of(7, 0), LocalTime.of(9, 30)),
                MealType.LUNCH, new Window(LocalTime.of(12, 0), LocalTime.of(14, 30)),
                MealType.DINNER, new Window(LocalTime.of(19, 0), LocalTime.of(21, 30))));
    }

    public Optional<MealType> mealAt(LocalTime time) {
        return windows.entrySet().stream()
                .filter(e -> e.getValue().contains(time))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
