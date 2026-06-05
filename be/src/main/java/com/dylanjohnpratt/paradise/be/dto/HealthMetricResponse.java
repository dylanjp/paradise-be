package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricPointSorter;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        // Display order must follow the label (date), not insertion order. We sort here
        // so existing rows persisted before the write-side fix still render chronologically;
        // we build sorted copies rather than mutating the entity to avoid dirty-flushing.
        List<String> labels = metric.getLabels();
        List<BigDecimal> data = metric.getData();
        List<Dataset> datasets = metric.getDatasets();
        int[] order = labels == null ? null : HealthMetricPointSorter.sortIndicesByLabelAscending(labels);
        if (order != null && labelsAlignWithData(labels, metric)) {
            labels = HealthMetricPointSorter.applyIndices(labels, order);
            if (data != null) {
                data = HealthMetricPointSorter.applyIndices(data, order);
            }
            if (datasets != null) {
                List<Dataset> reordered = new ArrayList<>(datasets.size());
                for (Dataset ds : datasets) {
                    reordered.add(new Dataset(ds.label(), HealthMetricPointSorter.applyIndices(ds.data(), order)));
                }
                datasets = reordered;
            }
        }

        List<HealthDatasetDto> datasetDtos = datasets == null
                ? null
                : datasets.stream().map(HealthDatasetDto::from).toList();
        return new HealthMetricResponse(
                metric.getId(),
                metric.getSlug(),
                metric.getName(),
                metric.getType(),
                metric.getUnit(),
                metric.getColors(),
                labels,
                data,
                datasetDtos,
                metric.isSeeded(),
                metric.getCreatedAt(),
                metric.getUpdatedAt()
        );
    }

    private static boolean labelsAlignWithData(List<String> labels, HealthMetric metric) {
        int n = labels.size();
        if (metric.getType().isSingleSeries()) {
            return metric.getData() != null && metric.getData().size() == n;
        }
        if (metric.getDatasets() == null) return false;
        for (Dataset ds : metric.getDatasets()) {
            if (ds.data() == null || ds.data().size() != n) return false;
        }
        return true;
    }
}
