package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthAppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Create payload for a medical appointment.
 */
public record HealthAppointmentRequest(
        @NotBlank String doctor,
        String specialty,
        @NotNull LocalDateTime apptDate,
        @NotNull HealthAppointmentType type,
        @Size(max = 4000) String notes
) {}
