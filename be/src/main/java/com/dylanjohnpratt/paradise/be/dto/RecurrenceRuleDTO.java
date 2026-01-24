package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule.RecurrenceType;

/**
 * DTO for recurrence rule data in notifications.
 * Supports daily, weekly, monthly, and randomized recurrence patterns.
 */
public record RecurrenceRuleDTO(
    RecurrenceType type,
    Integer dayOfWeek,
    Integer dayOfMonth
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
        return new RecurrenceRuleDTO(rule.getType(), rule.getDayOfWeek(), rule.getDayOfMonth());
    }

    /**
     * Converts this DTO to a RecurrenceRule entity.
     * Note: randomValuesInitialized will be false; use RecurrenceService to initialize random values.
     * @return the entity
     */
    public RecurrenceRule toEntity() {
        return new RecurrenceRule(type, dayOfWeek, dayOfMonth);
    }
}
