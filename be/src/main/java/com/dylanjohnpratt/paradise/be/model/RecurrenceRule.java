package com.dylanjohnpratt.paradise.be.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Value object representing a recurrence rule for notifications.
 * Supports daily, weekly, monthly, and randomized recurrence patterns.
 * Serializable to/from JSON for persistence.
 */
public class RecurrenceRule {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Defines the type of recurrence pattern.
     */
    public enum RecurrenceType {
        DAILY,
        WEEKLY,
        MONTHLY,
        RANDOM_WEEKLY,
        RANDOM_MONTHLY,
        YEARLY,
        RANDOM_DATE_RANGE
    }

    private final RecurrenceType type;

    // For WEEKLY: 1-7 (Monday-Sunday)
    // For RANDOM_WEEKLY: stored after random generation
    private final Integer dayOfWeek;

    // For MONTHLY: 1-31
    // For RANDOM_MONTHLY: stored after random generation
    // For YEARLY: 1-31 (day of the month for yearly recurrence)
    private final Integer dayOfMonth;

    // Flag indicating random values have been initialized
    private final boolean randomValuesInitialized;

    // For YEARLY: month (1-12)
    private final Integer month;

    // For RANDOM_DATE_RANGE: start month of date range (1-12)
    private final Integer startMonth;

    // For RANDOM_DATE_RANGE: start day of date range (1-31)
    private final Integer startDay;

    // For RANDOM_DATE_RANGE: end month of date range (1-12)
    private final Integer endMonth;

    // For RANDOM_DATE_RANGE: end day of date range (1-31)
    private final Integer endDay;

    // For RANDOM_DATE_RANGE: generated random month
    private final Integer randomMonth;

    // For RANDOM_DATE_RANGE: generated random day
    private final Integer randomDay;

    @JsonCreator
    public RecurrenceRule(
            @JsonProperty("type") RecurrenceType type,
            @JsonProperty("dayOfWeek") Integer dayOfWeek,
            @JsonProperty("dayOfMonth") Integer dayOfMonth,
            @JsonProperty("randomValuesInitialized") boolean randomValuesInitialized,
            @JsonProperty("month") Integer month,
            @JsonProperty("startMonth") Integer startMonth,
            @JsonProperty("startDay") Integer startDay,
            @JsonProperty("endMonth") Integer endMonth,
            @JsonProperty("endDay") Integer endDay,
            @JsonProperty("randomMonth") Integer randomMonth,
            @JsonProperty("randomDay") Integer randomDay) {
        this.type = type;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.randomValuesInitialized = randomValuesInitialized;
        this.month = month;
        this.startMonth = startMonth;
        this.startDay = startDay;
        this.endMonth = endMonth;
        this.endDay = endDay;
        this.randomMonth = randomMonth;
        this.randomDay = randomDay;
        validate();
    }


    /**
     * Convenience constructor for creating a RecurrenceRule without random initialization flag.
     */
    public RecurrenceRule(RecurrenceType type, Integer dayOfWeek, Integer dayOfMonth) {
        this(type, dayOfWeek, dayOfMonth, false, null, null, null, null, null, null, null);
    }

    /**
     * Convenience constructor for backward compatibility with existing code.
     */
    public RecurrenceRule(RecurrenceType type, Integer dayOfWeek, Integer dayOfMonth, boolean randomValuesInitialized) {
        this(type, dayOfWeek, dayOfMonth, randomValuesInitialized, null, null, null, null, null, null, null);
    }

    /**
     * Validates the recurrence rule based on its type.
     * @throws IllegalArgumentException if validation fails
     */
    private void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Recurrence type is required");
        }

        switch (type) {
            case DAILY:
                // No additional validation needed for daily
                break;
            case WEEKLY:
                validateDayOfWeek();
                break;
            case MONTHLY:
                validateDayOfMonth();
                break;
            case RANDOM_WEEKLY:
                if (randomValuesInitialized) {
                    validateDayOfWeek();
                }
                break;
            case RANDOM_MONTHLY:
                if (randomValuesInitialized) {
                    validateDayOfMonth();
                }
                break;
            case YEARLY:
                validateYearly();
                break;
            case RANDOM_DATE_RANGE:
                validateRandomDateRange();
                break;
        }
    }

    private void validateDayOfWeek() {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Day of week is required for weekly recurrence");
        }
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Day of week must be between 1 and 7");
        }
    }

    private void validateDayOfMonth() {
        if (dayOfMonth == null) {
            throw new IllegalArgumentException("Day of month is required for monthly recurrence");
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("Day of month must be between 1 and 31");
        }
    }

    private void validateYearly() {
        if (month == null) {
            throw new IllegalArgumentException("Month is required for yearly recurrence");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (dayOfMonth == null) {
            throw new IllegalArgumentException("Day of month is required for yearly recurrence");
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("Day of month must be between 1 and 31");
        }
        validateDayExistsInMonth(month, dayOfMonth);
    }

    private void validateRandomDateRange() {
        if (startMonth == null) {
            throw new IllegalArgumentException("Start month is required for date range recurrence");
        }
        if (startMonth < 1 || startMonth > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (startDay == null) {
            throw new IllegalArgumentException("Start day is required for date range recurrence");
        }
        if (startDay < 1 || startDay > 31) {
            throw new IllegalArgumentException("Day must be between 1 and 31");
        }
        if (endMonth == null) {
            throw new IllegalArgumentException("End month is required for date range recurrence");
        }
        if (endMonth < 1 || endMonth > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (endDay == null) {
            throw new IllegalArgumentException("End day is required for date range recurrence");
        }
        if (endDay < 1 || endDay > 31) {
            throw new IllegalArgumentException("Day must be between 1 and 31");
        }
        validateDayExistsInMonth(startMonth, startDay);
        validateDayExistsInMonth(endMonth, endDay);
        
        // Validate random values if initialized
        if (randomValuesInitialized) {
            if (randomMonth == null || randomDay == null) {
                throw new IllegalArgumentException("Random month and day must be set when initialized");
            }
            if (randomMonth < 1 || randomMonth > 12) {
                throw new IllegalArgumentException("Month must be between 1 and 12");
            }
            if (randomDay < 1 || randomDay > 31) {
                throw new IllegalArgumentException("Day must be between 1 and 31");
            }
            validateDayExistsInMonth(randomMonth, randomDay);
        }
    }

    /**
     * Validates that the specified day exists in the specified month.
     * Uses a non-leap year for validation (February has 28 days).
     * @param month the month (1-12)
     * @param day the day (1-31)
     * @throws IllegalArgumentException if the day does not exist in the month
     */
    private void validateDayExistsInMonth(int month, int day) {
        int maxDays = getMaxDaysInMonth(month);
        if (day > maxDays) {
            throw new IllegalArgumentException("Day " + day + " does not exist in month " + month);
        }
    }

    /**
     * Returns the maximum number of days in a given month.
     * Uses 28 for February (non-leap year) for validation purposes.
     * @param month the month (1-12)
     * @return the maximum number of days in the month
     */
    private int getMaxDaysInMonth(int month) {
        switch (month) {
            case 2:
                return 29; // Allow Feb 29 for leap year support
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    // Getters

    @JsonProperty("type")
    public RecurrenceType getType() {
        return type;
    }

    @JsonProperty("dayOfWeek")
    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    @JsonProperty("dayOfMonth")
    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    @JsonProperty("randomValuesInitialized")
    public boolean isRandomValuesInitialized() {
        return randomValuesInitialized;
    }

    @JsonProperty("month")
    public Integer getMonth() {
        return month;
    }

    @JsonProperty("startMonth")
    public Integer getStartMonth() {
        return startMonth;
    }

    @JsonProperty("startDay")
    public Integer getStartDay() {
        return startDay;
    }

    @JsonProperty("endMonth")
    public Integer getEndMonth() {
        return endMonth;
    }

    @JsonProperty("endDay")
    public Integer getEndDay() {
        return endDay;
    }

    @JsonProperty("randomMonth")
    public Integer getRandomMonth() {
        return randomMonth;
    }

    @JsonProperty("randomDay")
    public Integer getRandomDay() {
        return randomDay;
    }

    /**
     * Creates a new RecurrenceRule with random values initialized.
     * Used for RANDOM_WEEKLY and RANDOM_MONTHLY types.
     */
    public RecurrenceRule withRandomValuesInitialized(Integer dayOfWeek, Integer dayOfMonth) {
        return new RecurrenceRule(this.type, dayOfWeek, dayOfMonth, true,
                this.month, this.startMonth, this.startDay, this.endMonth, this.endDay,
                this.randomMonth, this.randomDay);
    }

    /**
     * Creates a new RecurrenceRule with random values initialized for RANDOM_DATE_RANGE type.
     * @param randomMonth the generated random month (1-12)
     * @param randomDay the generated random day (1-31)
     * @return a new RecurrenceRule with randomValuesInitialized=true and the random date set
     */
    public RecurrenceRule withRandomDateRangeInitialized(Integer randomMonth, Integer randomDay) {
        return new RecurrenceRule(this.type, this.dayOfWeek, this.dayOfMonth, true,
                this.month, this.startMonth, this.startDay, this.endMonth, this.endDay,
                randomMonth, randomDay);
    }

    /**
     * Serializes this RecurrenceRule to JSON string.
     * @return JSON representation of this rule
     * @throws RuntimeException if serialization fails
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize RecurrenceRule to JSON", e);
        }
    }

    /**
     * Deserializes a RecurrenceRule from JSON string.
     * @param json JSON representation of a RecurrenceRule
     * @return the deserialized RecurrenceRule
     * @throws RuntimeException if deserialization fails
     */
    public static RecurrenceRule fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, RecurrenceRule.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize RecurrenceRule from JSON", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecurrenceRule that = (RecurrenceRule) o;
        return randomValuesInitialized == that.randomValuesInitialized &&
                type == that.type &&
                Objects.equals(dayOfWeek, that.dayOfWeek) &&
                Objects.equals(dayOfMonth, that.dayOfMonth) &&
                Objects.equals(month, that.month) &&
                Objects.equals(startMonth, that.startMonth) &&
                Objects.equals(startDay, that.startDay) &&
                Objects.equals(endMonth, that.endMonth) &&
                Objects.equals(endDay, that.endDay) &&
                Objects.equals(randomMonth, that.randomMonth) &&
                Objects.equals(randomDay, that.randomDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, dayOfWeek, dayOfMonth, randomValuesInitialized,
                month, startMonth, startDay, endMonth, endDay, randomMonth, randomDay);
    }

    @Override
    public String toString() {
        return "RecurrenceRule{" +
                "type=" + type +
                ", dayOfWeek=" + dayOfWeek +
                ", dayOfMonth=" + dayOfMonth +
                ", randomValuesInitialized=" + randomValuesInitialized +
                ", month=" + month +
                ", startMonth=" + startMonth +
                ", startDay=" + startDay +
                ", endMonth=" + endMonth +
                ", endDay=" + endDay +
                ", randomMonth=" + randomMonth +
                ", randomDay=" + randomDay +
                '}';
    }
}
