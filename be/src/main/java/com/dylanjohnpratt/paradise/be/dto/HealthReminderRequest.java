package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Create payload for a health reminder.
 */
public record HealthReminderRequest(
        @NotBlank String title,
        @Size(max = 4000) String description,
        LocalDateTime dueAt,
        Boolean completed
) {}
