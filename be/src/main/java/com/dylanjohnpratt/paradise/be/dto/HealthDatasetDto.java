package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-boundary mirror of {@link Dataset}, used in multi-series
 * {@link HealthMetricRequest}/{@link HealthMetricResponse} payloads.
 */
public record HealthDatasetDto(
        @NotBlank String label,
        @NotNull List<BigDecimal> data
) {
    public static HealthDatasetDto from(Dataset dataset) {
        return new HealthDatasetDto(dataset.label(), dataset.data());
    }

    public Dataset toModel() {
        return new Dataset(label, data);
    }
}
