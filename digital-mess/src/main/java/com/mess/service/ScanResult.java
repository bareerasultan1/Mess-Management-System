package com.mess.service;

import com.mess.domain.MealType;
import com.mess.domain.ScanType;

import java.time.Instant;

/** What the terminal shows after an accepted scan. */
public record ScanResult(ScanType scanType, MealType mealType, String studentName, Instant at) { }
