package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule.RecurrenceType;

/**
 * DTO for recurrence rule data in notifications.
 * Supports daily, weekly, monthly, yearly, and randomized recurrence patterns.
 */
public record RecurrenceRuleDTO(
    RecurrenceType type,
    Integer dayOfWeek,
    Integer dayOfMonth,
    Integer month,        // For YEARLY recurrence
    Integer startMonth,   // For RANDOM_DATE_RANGE
    Integer startDay,     // For RANDOM_DATE_RANGE
    Integer endMonth,     // For RANDOM_DATE_RANGE
    Integer endDay,       // For RANDOM_DATE_RANGE
    Integer randomMonth,  // For RANDOM_DATE_RANGE (generated random month)
    Integer randomDay     // For RANDOM_DATE_RANGE (generated random day)
) {
    /**
     * Creates a RecurrenceRuleDTO from a RecurrenceRule entity.
     * @param rule the entity to convert
     * @return the DTO, or null if the entity is null
     */
    public static RecurrenceRuleDTO fromEntity(RecurrenceRule rule) {
        if (rule == null) {
            return null;
        }
        return new RecurrenceRuleDTO(
            rule.getType(),
            rule.getDayOfWeek(),
            rule.getDayOfMonth(),
            rule.getMonth(),
            rule.getStartMonth(),
            rule.getStartDay(),
            rule.getEndMonth(),
            rule.getEndDay(),
            rule.getRandomMonth(),
            rule.getRandomDay()
        );
    }

    /**
     * Converts this DTO to a RecurrenceRule entity.
     * Note: randomValuesInitialized will be false; use RecurrenceService to initialize random values.
     * @return the entity
     */
    public RecurrenceRule toEntity() {
        return new RecurrenceRule(
            type,
            dayOfWeek,
            dayOfMonth,
            false,  // randomValuesInitialized - always false from DTO
            month,
            startMonth,
            startDay,
            endMonth,
            endDay,
            randomMonth,
            randomDay
        );
    }
}
