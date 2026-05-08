package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthReminder;

import java.time.LocalDateTime;

/**
 * Response payload for a health reminder.
 */
public record HealthReminderResponse(
        String id,
        String title,
        String description,
        LocalDateTime dueAt,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthReminderResponse from(HealthReminder reminder) {
        return new HealthReminderResponse(
                reminder.getId(),
                reminder.getTitle(),
                reminder.getDescription(),
                reminder.getDueAt(),
                reminder.isCompleted(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
