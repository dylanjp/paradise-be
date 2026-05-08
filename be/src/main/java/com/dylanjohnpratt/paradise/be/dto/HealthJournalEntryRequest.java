package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Upsert payload for a daily health journal entry. POSTing with the same
 * {@code date} as an existing entry overwrites it.
 */
public record HealthJournalEntryRequest(
        @NotNull LocalDate date,
        BigDecimal weightLbs,
        LocalTime bedTime,
        LocalTime wakeTime,
        @Min(0) @Max(10) Short energy,
        @Min(1) @Max(5) Short mood,
        @Size(max = 10000) String thoughts
) {}
