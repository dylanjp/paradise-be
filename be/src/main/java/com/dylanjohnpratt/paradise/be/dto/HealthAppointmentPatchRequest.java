package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthAppointmentType;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * PATCH payload for partial appointment updates. All fields are optional;
 * null fields are left unchanged.
 */
public record HealthAppointmentPatchRequest(
        String doctor,
        String specialty,
        LocalDateTime apptDate,
        HealthAppointmentType type,
        @Size(max = 4000) String notes
) {}
