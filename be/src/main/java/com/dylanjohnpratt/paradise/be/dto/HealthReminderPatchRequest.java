package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * PATCH payload for partial reminder updates. All fields are optional;
 * null fields are left unchanged.
 */
public record HealthReminderPatchRequest(
        String title,
        @Size(max = 4000) String description,
        LocalDateTime dueAt,
        Boolean completed
) {}
