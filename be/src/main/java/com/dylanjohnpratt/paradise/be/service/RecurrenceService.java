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
            case YEARLY -> evaluateYearlyRecurrence(rule, localDate);
            case RANDOM_DATE_RANGE -> evaluateRandomDateRangeRecurrence(rule, localDate);
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
            case YEARLY -> rule;  // YEARLY doesn't need random initialization
            case RANDOM_DATE_RANGE -> {
                if (rule.isRandomValuesInitialized()) {
                    yield rule;  // Already initialized
                }
                // Generate random date within the specified range
                int[] randomDate = generateRandomDateInRange(
                        rule.getStartMonth(), rule.getStartDay(),
                        rule.getEndMonth(), rule.getEndDay());
                yield rule.withRandomDateRangeInitialized(randomDate[0], randomDate[1]);
            }
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

        // Handle YEARLY and RANDOM_DATE_RANGE with optimized logic
        if (rule.getType() == RecurrenceRule.RecurrenceType.YEARLY) {
            return getNextYearlyDeliveryDate(rule, localFromDate);
        }
        if (rule.getType() == RecurrenceRule.RecurrenceType.RANDOM_DATE_RANGE) {
            return getNextRandomDateRangeDeliveryDate(rule, localFromDate);
        }

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
     * Gets the next delivery date for a YEARLY recurrence rule.
     * Handles leap year for Feb 29 by skipping to the next leap year if needed.
     * 
     * @param rule the YEARLY recurrence rule
     * @param fromDate the date to start searching from (inclusive)
     * @return the next delivery date
     */
    private Optional<LocalDate> getNextYearlyDeliveryDate(RecurrenceRule rule, LocalDate fromDate) {
        Integer month = rule.getMonth();
        Integer dayOfMonth = rule.getDayOfMonth();
        
        if (month == null || dayOfMonth == null) {
            return Optional.empty();
        }
        
        // Special handling for Feb 29 (leap year)
        if (month == 2 && dayOfMonth == 29) {
            return getNextLeapYearFeb29(fromDate);
        }
        
        // Try current year first
        LocalDate targetThisYear = LocalDate.of(fromDate.getYear(), month, dayOfMonth);
        if (!targetThisYear.isBefore(fromDate)) {
            return Optional.of(targetThisYear);
        }
        
        // Otherwise, return next year
        return Optional.of(LocalDate.of(fromDate.getYear() + 1, month, dayOfMonth));
    }

    /**
     * Gets the next Feb 29 date (leap year only).
     */
    private Optional<LocalDate> getNextLeapYearFeb29(LocalDate fromDate) {
        int year = fromDate.getYear();
        
        // Check if this year is a leap year and Feb 29 hasn't passed
        if (isLeapYear(year)) {
            LocalDate feb29ThisYear = LocalDate.of(year, 2, 29);
            if (!feb29ThisYear.isBefore(fromDate)) {
                return Optional.of(feb29ThisYear);
            }
        }
        
        // Find the next leap year
        int nextLeapYear = year + 1;
        while (!isLeapYear(nextLeapYear)) {
            nextLeapYear++;
        }
        
        return Optional.of(LocalDate.of(nextLeapYear, 2, 29));
    }

    /**
     * Checks if a year is a leap year.
     */
    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    /**
     * Gets the next delivery date for a RANDOM_DATE_RANGE recurrence rule.
     * Uses the stored randomMonth/randomDay as the target date.
     * 
     * @param rule the RANDOM_DATE_RANGE recurrence rule (must be initialized)
     * @param fromDate the date to start searching from (inclusive)
     * @return the next delivery date
     */
    private Optional<LocalDate> getNextRandomDateRangeDeliveryDate(RecurrenceRule rule, LocalDate fromDate) {
        if (!rule.isRandomValuesInitialized()) {
            throw new IllegalStateException("Random values must be initialized before evaluation");
        }
        
        Integer randomMonth = rule.getRandomMonth();
        Integer randomDay = rule.getRandomDay();
        
        if (randomMonth == null || randomDay == null) {
            return Optional.empty();
        }
        
        // Try current year first
        LocalDate targetThisYear = LocalDate.of(fromDate.getYear(), randomMonth, randomDay);
        if (!targetThisYear.isBefore(fromDate)) {
            return Optional.of(targetThisYear);
        }
        
        // Otherwise, return next year
        return Optional.of(LocalDate.of(fromDate.getYear() + 1, randomMonth, randomDay));
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

    /**
     * Evaluates yearly recurrence - delivers on the specified month and day.
     * Returns true only if both the month and day match.
     * 
     * @param rule the recurrence rule containing month and dayOfMonth
     * @param date the date to check
     * @return true if the date matches the yearly recurrence pattern
     */
    private boolean evaluateYearlyRecurrence(RecurrenceRule rule, LocalDate date) {
        Integer month = rule.getMonth();
        Integer dayOfMonth = rule.getDayOfMonth();
        
        if (month == null || dayOfMonth == null) {
            return false;
        }
        
        return date.getMonthValue() == month && date.getDayOfMonth() == dayOfMonth;
    }

    /**
     * Evaluates random date range recurrence - delivers on the stored random month and day.
     * Requires random values to be initialized first.
     * 
     * @param rule the recurrence rule containing randomMonth and randomDay
     * @param date the date to check
     * @return true if the date matches the stored random date
     * @throws IllegalStateException if random values have not been initialized
     */
    private boolean evaluateRandomDateRangeRecurrence(RecurrenceRule rule, LocalDate date) {
        if (!rule.isRandomValuesInitialized()) {
            throw new IllegalStateException("Random values must be initialized before evaluation");
        }
        
        Integer randomMonth = rule.getRandomMonth();
        Integer randomDay = rule.getRandomDay();
        
        if (randomMonth == null || randomDay == null) {
            return false;
        }
        
        return date.getMonthValue() == randomMonth && date.getDayOfMonth() == randomDay;
    }

    /**
     * Generates a random date within the specified range.
     * Handles cross-year ranges (e.g., December 15 to January 15).
     * 
     * @param startMonth start month of range (1-12)
     * @param startDay start day of range (1-31)
     * @param endMonth end month of range (1-12)
     * @param endDay end day of range (1-31)
     * @return array of [month, day] representing the random date
     */
    private int[] generateRandomDateInRange(int startMonth, int startDay, int endMonth, int endDay) {
        // Calculate total days in the range
        int totalDays = calculateDaysInRange(startMonth, startDay, endMonth, endDay);
        
        // Generate random day index (0-based)
        int randomIndex = random.nextInt(totalDays);
        
        // Convert index back to month/day
        return indexToMonthDay(startMonth, startDay, endMonth, endDay, randomIndex);
    }

    /**
     * Calculates the total number of days in a date range.
     * Handles cross-year ranges where startMonth > endMonth.
     * Uses a non-leap year (365 days) for calculation.
     */
    private int calculateDaysInRange(int startMonth, int startDay, int endMonth, int endDay) {
        if (startMonth < endMonth || (startMonth == endMonth && startDay <= endDay)) {
            // Same year range (e.g., Jan 15 to Mar 20)
            return dayOfYear(endMonth, endDay) - dayOfYear(startMonth, startDay) + 1;
        } else {
            // Cross-year range (e.g., Dec 15 to Jan 15)
            // Days from start to end of year + days from start of year to end
            int daysToEndOfYear = 365 - dayOfYear(startMonth, startDay) + 1;
            int daysFromStartOfYear = dayOfYear(endMonth, endDay);
            return daysToEndOfYear + daysFromStartOfYear;
        }
    }

    /**
     * Converts a month/day to day of year (1-365).
     * Uses standard non-leap year day counts.
     */
    private int dayOfYear(int month, int day) {
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int dayOfYear = day;
        for (int m = 0; m < month - 1; m++) {
            dayOfYear += daysInMonth[m];
        }
        return dayOfYear;
    }

    /**
     * Converts a random index back to month/day within the specified range.
     * Handles cross-year ranges.
     */
    private int[] indexToMonthDay(int startMonth, int startDay, int endMonth, int endDay, int index) {
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        
        int currentMonth = startMonth;
        int currentDay = startDay;
        int remaining = index;
        
        while (remaining > 0) {
            int daysLeftInMonth = daysInMonth[currentMonth - 1] - currentDay + 1;
            
            if (remaining < daysLeftInMonth) {
                currentDay += remaining;
                remaining = 0;
            } else {
                remaining -= daysLeftInMonth;
                currentMonth++;
                if (currentMonth > 12) {
                    currentMonth = 1;  // Wrap to January
                }
                currentDay = 1;
            }
        }
        
        return new int[]{currentMonth, currentDay};
    }
}
