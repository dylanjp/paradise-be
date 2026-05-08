package com.dylanjohnpratt.paradise.be.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Append-a-point payload for a metric.
 * <p>
 * Shape depends on the metric's type:
 * <ul>
 *   <li><b>Single-series</b> ({@code line}, {@code bar}): supply {@link #value()};
 *       {@link #values()} must be omitted/empty.</li>
 *   <li><b>Multi-series</b> ({@code dual-line}, {@code multi-line}): supply
 *       {@link #values()}, with one entry per existing dataset. Every dataset's
 *       label must be present exactly once. {@link #value()} must be omitted.</li>
 * </ul>
 * The optional {@link #label()} is appended to the metric's shared x-axis labels
 * list (same list of tick labels rendered across all datasets).
 */
public record HealthMetricPointRequest(
        BigDecimal value,
        @Valid List<MultiSeriesValue> values,
        String label
) {
    /**
     * One value targeted at a specific dataset within a multi-series metric.
     * The {@code label} must match an existing {@code Dataset.label} on the
     * metric (e.g. {@code "Systolic"}, {@code "Diastolic"}).
     */
    public record MultiSeriesValue(
            @NotBlank String label,
            @NotNull BigDecimal value
    ) {}
}
