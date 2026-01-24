package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * Service for evaluating recurrence rules and determining notification delivery schedules.
 * Handles daily, weekly, monthly, and randomized recurrence patterns.
 * Supports time zone conversions for accurate delivery scheduling.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.10, 3.11
 */
@Service
public class RecurrenceService {

    private final Random random;

    public RecurrenceService() {
        this.random = new Random();
    }

    /**
     * Constructor for testing with a seeded random generator.
     * @param random the random generator to use
     */
    public RecurrenceService(Random random) {
        this.random = random;
    }

    /**
     * Evaluates if a recurring notification should be delivered on a given date.
     * Converts the date to the user's time zone for accurate evaluation.
     * 
     * @param rule the recurrence rule to evaluate
     * @param date the date to check for delivery
     * @param userTimeZone the user's time zone for date conversion
     * @return true if the notification should be delivered on the given date
     * @throws IllegalArgumentException if rule is null or random values not initialized for random types
     */
    public boolean shouldDeliverOn(RecurrenceRule rule, LocalDate date, ZoneId userTimeZone) {
        if (rule == null) {
            throw new IllegalArgumentException("Recurrence rule cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        // Convert date to user's time zone for accurate evaluation
        LocalDate localDate = convertToUserTimeZone(date, userTimeZone);

        return switch (rule.getType()) {
            case DAILY -> true;  // Daily recurrence delivers every day
            case WEEKLY -> evaluateWeeklyRecurrence(rule, localDate);
            case MONTHLY -> evaluateMonthlyRecurrence(rule, localDate);
            case RANDOM_WEEKLY -> evaluateRandomWeeklyRecurrence(rule, localDate);
            case RANDOM_MONTHLY -> evaluateRandomMonthlyRecurrence(rule, localDate);
        };
    }


    /**
     * Generates and stores random values for randomized recurrence types.
     * For RANDOM_WEEKLY: generates a random day of week (1-7, Monday-Sunday)
     * For RANDOM_MONTHLY: generates a random day of month (1-28 to ensure validity in all months)
     * 
     * @param rule the recurrence rule to initialize
     * @return a new RecurrenceRule with random values initialized
     * @throws IllegalArgumentException if rule is null or not a random type
     */
    public RecurrenceRule initializeRandomValues(RecurrenceRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Recurrence rule cannot be null");
        }

        return switch (rule.getType()) {
            case RANDOM_WEEKLY -> {
                if (rule.isRandomValuesInitialized()) {
                    yield rule;  // Already initialized
                }
                // Generate random day of week (1-7)
                int randomDayOfWeek = random.nextInt(7) + 1;
                yield rule.withRandomValuesInitialized(randomDayOfWeek, null);
            }
            case RANDOM_MONTHLY -> {
                if (rule.isRandomValuesInitialized()) {
                    yield rule;  // Already initialized
                }
                // Generate random day of month (1-28 to ensure validity in all months)
                int randomDayOfMonth = random.nextInt(28) + 1;
                yield rule.withRandomValuesInitialized(null, randomDayOfMonth);
            }
            case DAILY, WEEKLY, MONTHLY -> rule;  // Non-random types don't need initialization
        };
    }

    /**
     * Gets the next delivery date for a recurring notification starting from a given date.
     * Returns empty if no valid delivery date exists (e.g., for invalid rules).
     * 
     * @param rule the recurrence rule to evaluate
     * @param fromDate the date to start searching from (inclusive)
     * @param userTimeZone the user's time zone for date conversion
     * @return the next delivery date, or empty if none found within reasonable range
     */
    public Optional<LocalDate> getNextDeliveryDate(RecurrenceRule rule, LocalDate fromDate, ZoneId userTimeZone) {
        if (rule == null || fromDate == null) {
            return Optional.empty();
        }

        LocalDate localFromDate = convertToUserTimeZone(fromDate, userTimeZone);

        // Check up to 366 days ahead (covers all recurrence patterns)
        for (int i = 0; i <= 366; i++) {
            LocalDate candidateDate = localFromDate.plusDays(i);
            if (shouldDeliverOn(rule, candidateDate, userTimeZone)) {
                return Optional.of(candidateDate);
            }
        }

        return Optional.empty();
    }

    /**
     * Converts a date to the user's time zone.
     * If userTimeZone is null, returns the date unchanged.
     * 
     * @param date the date to convert
     * @param userTimeZone the target time zone
     * @return the date in the user's time zone
     */
    private LocalDate convertToUserTimeZone(LocalDate date, ZoneId userTimeZone) {
        if (userTimeZone == null) {
            return date;
        }
        // Convert the date at start of day in system default zone to user's zone
        ZonedDateTime systemZoned = date.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime userZoned = systemZoned.withZoneSameInstant(userTimeZone);
        return userZoned.toLocalDate();
    }


    /**
     * Evaluates weekly recurrence - delivers on the specified day of week.
     * Day of week: 1=Monday, 2=Tuesday, ..., 7=Sunday
     */
    private boolean evaluateWeeklyRecurrence(RecurrenceRule rule, LocalDate date) {
        Integer dayOfWeek = rule.getDayOfWeek();
        if (dayOfWeek == null) {
            return false;
        }
        // Convert Java DayOfWeek (1=Monday to 7=Sunday) to match our rule format
        return date.getDayOfWeek().getValue() == dayOfWeek;
    }

    /**
     * Evaluates monthly recurrence - delivers on the specified day of month.
     * Handles months with fewer days by not delivering if the day doesn't exist.
     */
    private boolean evaluateMonthlyRecurrence(RecurrenceRule rule, LocalDate date) {
        Integer dayOfMonth = rule.getDayOfMonth();
        if (dayOfMonth == null) {
            return false;
        }
        // Check if the day exists in this month
        int maxDayInMonth = date.lengthOfMonth();
        if (dayOfMonth > maxDayInMonth) {
            return false;  // Day doesn't exist in this month
        }
        return date.getDayOfMonth() == dayOfMonth;
    }

    /**
     * Evaluates random weekly recurrence - uses the stored random day of week.
     * Requires random values to be initialized first.
     */
    private boolean evaluateRandomWeeklyRecurrence(RecurrenceRule rule, LocalDate date) {
        if (!rule.isRandomValuesInitialized()) {
            throw new IllegalStateException("Random values must be initialized before evaluation");
        }
        Integer dayOfWeek = rule.getDayOfWeek();
        if (dayOfWeek == null) {
            return false;
        }
        return date.getDayOfWeek().getValue() == dayOfWeek;
    }

    /**
     * Evaluates random monthly recurrence - uses the stored random day of month.
     * Requires random values to be initialized first.
     * Handles months with fewer days by not delivering if the day doesn't exist.
     */
    private boolean evaluateRandomMonthlyRecurrence(RecurrenceRule rule, LocalDate date) {
        if (!rule.isRandomValuesInitialized()) {
            throw new IllegalStateException("Random values must be initialized before evaluation");
        }
        Integer dayOfMonth = rule.getDayOfMonth();
        if (dayOfMonth == null) {
            return false;
        }
        // Check if the day exists in this month
        int maxDayInMonth = date.lengthOfMonth();
        if (dayOfMonth > maxDayInMonth) {
            return false;  // Day doesn't exist in this month
        }
        return date.getDayOfMonth() == dayOfMonth;
    }
}
