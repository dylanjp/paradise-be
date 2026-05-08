package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for a health metric (chart). Only one of
 * {@code data} / {@code datasets} is populated, matching the metric's {@code type}.
 */
public record HealthMetricResponse(
        String id,
        String slug,
        String name,
        HealthMetricType type,
        String unit,
        List<String> colors,
        List<String> labels,
        List<BigDecimal> data,
        List<HealthDatasetDto> datasets,
        boolean seeded,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HealthMetricResponse from(HealthMetric metric) {
        List<HealthDatasetDto> datasetDtos = metric.getDatasets() == null
                ? null
                : metric.getDatasets().stream().map(HealthDatasetDto::from).toList();
        return new HealthMetricResponse(
                metric.getId(),
                metric.getSlug(),
                metric.getName(),
                metric.getType(),
                metric.getUnit(),
                metric.getColors(),
                metric.getLabels(),
                metric.getData(),
                datasetDtos,
                metric.isSeeded(),
                metric.getCreatedAt(),
                metric.getUpdatedAt()
        );
    }
}
