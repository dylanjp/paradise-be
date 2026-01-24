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
        RANDOM_MONTHLY
    }

    private final RecurrenceType type;

    // For WEEKLY: 1-7 (Monday-Sunday)
    // For RANDOM_WEEKLY: stored after random generation
    private final Integer dayOfWeek;

    // For MONTHLY: 1-31
    // For RANDOM_MONTHLY: stored after random generation
    private final Integer dayOfMonth;

    // Flag indicating random values have been initialized
    private final boolean randomValuesInitialized;

    @JsonCreator
    public RecurrenceRule(
            @JsonProperty("type") RecurrenceType type,
            @JsonProperty("dayOfWeek") Integer dayOfWeek,
            @JsonProperty("dayOfMonth") Integer dayOfMonth,
            @JsonProperty("randomValuesInitialized") boolean randomValuesInitialized) {
        this.type = type;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.randomValuesInitialized = randomValuesInitialized;
        validate();
    }


    /**
     * Convenience constructor for creating a RecurrenceRule without random initialization flag.
     */
    public RecurrenceRule(RecurrenceType type, Integer dayOfWeek, Integer dayOfMonth) {
        this(type, dayOfWeek, dayOfMonth, false);
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

    /**
     * Creates a new RecurrenceRule with random values initialized.
     * Used for RANDOM_WEEKLY and RANDOM_MONTHLY types.
     */
    public RecurrenceRule withRandomValuesInitialized(Integer dayOfWeek, Integer dayOfMonth) {
        return new RecurrenceRule(this.type, dayOfWeek, dayOfMonth, true);
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
                Objects.equals(dayOfMonth, that.dayOfMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, dayOfWeek, dayOfMonth, randomValuesInitialized);
    }

    @Override
    public String toString() {
        return "RecurrenceRule{" +
                "type=" + type +
                ", dayOfWeek=" + dayOfWeek +
                ", dayOfMonth=" + dayOfMonth +
                ", randomValuesInitialized=" + randomValuesInitialized +
                '}';
    }
}
