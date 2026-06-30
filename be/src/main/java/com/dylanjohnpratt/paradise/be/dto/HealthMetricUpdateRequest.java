package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Update payload for an existing health metric's settings.
 * <p>
 * Settings-only: {@code name}, {@code unit}, and {@code colors} may change.
 * The metric's {@code type} and its data ({@code data}/{@code datasets}/{@code labels})
 * are intentionally not editable here — changing the type would require migrating
 * the stored series shape. Point edits go through the points endpoints instead.
 */
public record HealthMetricUpdateRequest(
        @NotBlank String name,
        String unit,
        List<String> colors
) {}
