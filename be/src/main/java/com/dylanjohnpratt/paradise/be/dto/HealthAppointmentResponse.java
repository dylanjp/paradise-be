package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthAppointment;
import com.dylanjohnpratt.paradise.be.health.model.HealthAppointmentType;

import java.time.LocalDateTime;

/**
 * Response payload for a medical appointment.
 */
public record HealthAppointmentResponse(
        String id,
        String doctor,
        String specialty,
        LocalDateTime apptDate,
        HealthAppointmentType type,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthAppointmentResponse from(HealthAppointment appt) {
        return new HealthAppointmentResponse(
                appt.getId(),
                appt.getDoctor(),
                appt.getSpecialty(),
                appt.getApptDate(),
                appt.getType(),
                appt.getNotes(),
                appt.getCreatedAt(),
                appt.getUpdatedAt()
        );
    }
}
