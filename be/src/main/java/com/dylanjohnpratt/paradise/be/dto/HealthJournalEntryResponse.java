package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthJournalEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Response payload for a single health journal entry.
 */
public record HealthJournalEntryResponse(
        String id,
        LocalDate date,
        BigDecimal weightLbs,
        LocalTime bedTime,
        LocalTime wakeTime,
        Short energy,
        Short mood,
        String thoughts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthJournalEntryResponse from(HealthJournalEntry entry) {
        return new HealthJournalEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getWeightLbs(),
                entry.getBedTime(),
                entry.getWakeTime(),
                entry.getEnergy(),
                entry.getMood(),
                entry.getThoughts(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
