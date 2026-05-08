package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create payload for a user-defined health metric.
 * <p>
 * Exactly one of {@code data} (for {@link HealthMetricType#LINE}/{@link HealthMetricType#BAR})
 * or {@code datasets} (for {@link HealthMetricType#DUAL_LINE}/{@link HealthMetricType#MULTI_LINE})
 * must be present. The service layer enforces this invariant and throws
 * {@link com.dylanjohnpratt.paradise.be.exception.HealthValidationException} on mismatch.
 */
public record HealthMetricRequest(
        @NotBlank String name,
        @NotNull HealthMetricType type,
        String unit,
        List<String> colors,
        List<String> labels,
        List<BigDecimal> data,
        @Valid List<HealthDatasetDto> datasets
) {}
