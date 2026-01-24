package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule.RecurrenceType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for RecurrenceService.
 * Uses jqwik to verify correctness properties across many generated inputs.
 * 
 * Validates: Requirements 3.1, 3.2, 3.3
 */
class RecurrenceServicePropertyTest {

    /**
     * Creates a RecurrenceService with a seeded random for deterministic testing.
     */
    private RecurrenceService createTestService() {
        return new RecurrenceService(new Random(42));
    }

    /**
     * Feature: notification-service, Property 7: Daily Recurrence Delivery
     * For any notification with daily recurrence, the notification SHALL be delivered
     * on every consecutive day within its validity period.
     * 
     * Validates: Requirements 3.1
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 7: Daily Recurrence Delivery")
    void dailyRecurrenceDeliversEveryDay(
            @ForAll("validDates") LocalDate date
    ) {
        RecurrenceService service = createTestService();
        RecurrenceRule dailyRule = new RecurrenceRule(RecurrenceType.DAILY, null, null);
        
        // Daily recurrence should deliver on any date
        boolean shouldDeliver = service.shouldDeliverOn(dailyRule, date, ZoneId.systemDefault());
        
        assertThat(shouldDeliver)
                .as("Daily recurrence should deliver on date %s", date)
                .isTrue();
    }


    /**
     * Feature: notification-service, Property 8: Weekly Recurrence Delivery
     * For any notification with weekly recurrence specifying day D, the notification
     * SHALL be delivered only on days where the day of week equals D.
     * 
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 8: Weekly Recurrence Delivery")
    void weeklyRecurrenceDeliversOnSpecifiedDay(
            @ForAll @IntRange(min = 1, max = 7) int dayOfWeek,
            @ForAll("validDates") LocalDate date
    ) {
        RecurrenceService service = createTestService();
        RecurrenceRule weeklyRule = new RecurrenceRule(RecurrenceType.WEEKLY, dayOfWeek, null);
        
        boolean shouldDeliver = service.shouldDeliverOn(weeklyRule, date, ZoneId.systemDefault());
        int actualDayOfWeek = date.getDayOfWeek().getValue();
        
        if (actualDayOfWeek == dayOfWeek) {
            assertThat(shouldDeliver)
                    .as("Weekly recurrence for day %d should deliver on %s (day %d)", 
                        dayOfWeek, date, actualDayOfWeek)
                    .isTrue();
        } else {
            assertThat(shouldDeliver)
                    .as("Weekly recurrence for day %d should NOT deliver on %s (day %d)", 
                        dayOfWeek, date, actualDayOfWeek)
                    .isFalse();
        }
    }

    /**
     * Feature: notification-service, Property 9: Monthly Recurrence Delivery
     * For any notification with monthly recurrence specifying day D, the notification
     * SHALL be delivered only on days where the day of month equals D.
     * 
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 9: Monthly Recurrence Delivery")
    void monthlyRecurrenceDeliversOnSpecifiedDay(
            @ForAll @IntRange(min = 1, max = 28) int dayOfMonth,
            @ForAll("validDates") LocalDate date
    ) {
        RecurrenceService service = createTestService();
        RecurrenceRule monthlyRule = new RecurrenceRule(RecurrenceType.MONTHLY, null, dayOfMonth);
        
        boolean shouldDeliver = service.shouldDeliverOn(monthlyRule, date, ZoneId.systemDefault());
        int actualDayOfMonth = date.getDayOfMonth();
        int maxDayInMonth = date.lengthOfMonth();
        
        // If the specified day doesn't exist in this month, should not deliver
        if (dayOfMonth > maxDayInMonth) {
            assertThat(shouldDeliver)
                    .as("Monthly recurrence for day %d should NOT deliver in month with %d days", 
                        dayOfMonth, maxDayInMonth)
                    .isFalse();
        } else if (actualDayOfMonth == dayOfMonth) {
            assertThat(shouldDeliver)
                    .as("Monthly recurrence for day %d should deliver on %s (day %d)", 
                        dayOfMonth, date, actualDayOfMonth)
                    .isTrue();
        } else {
            assertThat(shouldDeliver)
                    .as("Monthly recurrence for day %d should NOT deliver on %s (day %d)", 
                        dayOfMonth, date, actualDayOfMonth)
                    .isFalse();
        }
    }

    /**
     * Provides valid dates for testing (within a reasonable range).
     */
    @Provide
    Arbitrary<LocalDate> validDates() {
        return Arbitraries.integers()
                .between(0, 365 * 10)  // Up to 10 years from epoch
                .map(days -> LocalDate.of(2020, 1, 1).plusDays(days));
    }
}


/**
 * Additional property tests for randomized recurrence patterns.
 * 
 * Validates: Requirements 3.4, 3.5, 3.6, 3.7
 */
class RecurrenceServiceRandomizedPropertyTest {

    /**
     * Feature: notification-service, Property 10: Randomized Recurrence Value Persistence
     * For any notification with randomized recurrence (weekly or monthly), after creation
     * the random day value SHALL be stored and SHALL be within valid range
     * (1-7 for weekly, 1-28 for monthly).
     * 
     * Validates: Requirements 3.4, 3.5, 3.6
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 10: Randomized Recurrence Value Persistence")
    void randomizedRecurrenceValuePersistence(
            @ForAll("randomRecurrenceTypes") RecurrenceType type,
            @ForAll @IntRange(min = 1, max = 1000) int seed
    ) {
        RecurrenceService service = new RecurrenceService(new Random(seed));
        
        RecurrenceRule uninitializedRule = new RecurrenceRule(type, null, null);
        RecurrenceRule initializedRule = service.initializeRandomValues(uninitializedRule);
        
        assertThat(initializedRule.isRandomValuesInitialized())
                .as("Random values should be initialized")
                .isTrue();
        
        if (type == RecurrenceType.RANDOM_WEEKLY) {
            assertThat(initializedRule.getDayOfWeek())
                    .as("Random day of week should be between 1 and 7")
                    .isBetween(1, 7);
        } else if (type == RecurrenceType.RANDOM_MONTHLY) {
            assertThat(initializedRule.getDayOfMonth())
                    .as("Random day of month should be between 1 and 28")
                    .isBetween(1, 28);
        }
    }

    /**
     * Feature: notification-service, Property 11: Deterministic Random Recurrence Evaluation
     * For any notification with randomized recurrence, evaluating the recurrence rule
     * multiple times with the same date SHALL produce identical results.
     * 
     * Validates: Requirements 3.7
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 11: Deterministic Random Recurrence Evaluation")
    void deterministicRandomRecurrenceEvaluation(
            @ForAll("randomRecurrenceTypes") RecurrenceType type,
            @ForAll @IntRange(min = 1, max = 1000) int seed,
            @ForAll("validDates") LocalDate date
    ) {
        RecurrenceService service = new RecurrenceService(new Random(seed));
        
        // Initialize the rule once
        RecurrenceRule uninitializedRule = new RecurrenceRule(type, null, null);
        RecurrenceRule initializedRule = service.initializeRandomValues(uninitializedRule);
        
        // Evaluate multiple times with the same date
        boolean result1 = service.shouldDeliverOn(initializedRule, date, ZoneId.systemDefault());
        boolean result2 = service.shouldDeliverOn(initializedRule, date, ZoneId.systemDefault());
        boolean result3 = service.shouldDeliverOn(initializedRule, date, ZoneId.systemDefault());
        
        assertThat(result1)
                .as("Multiple evaluations should produce identical results")
                .isEqualTo(result2)
                .isEqualTo(result3);
    }

    /**
     * Provides random recurrence types for testing.
     */
    @Provide
    Arbitrary<RecurrenceType> randomRecurrenceTypes() {
        return Arbitraries.of(RecurrenceType.RANDOM_WEEKLY, RecurrenceType.RANDOM_MONTHLY);
    }

    /**
     * Provides valid dates for testing (within a reasonable range).
     */
    @Provide
    Arbitrary<LocalDate> validDates() {
        return Arbitraries.integers()
                .between(0, 365 * 10)  // Up to 10 years from epoch
                .map(days -> LocalDate.of(2020, 1, 1).plusDays(days));
    }
}
