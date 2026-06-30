package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthDatasetDto;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricResponse;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricUpdateRequest;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthSeededMetricLockedException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricPointSorter;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for user-scoped health metrics (charts).
 * <p>
 * Enforces:
 * <ul>
 *   <li>Single-series ({@link HealthMetricType#LINE}, {@link HealthMetricType#BAR}) use {@code data}
 *       and must not carry {@code datasets}, and vice versa for multi-series types.</li>
 *   <li>{@link HealthMetric#isSeeded() seeded} metrics cannot be deleted.</li>
 *   <li>{@code POST /metrics/{id}/points} is valid only for single-series metrics.</li>
 * </ul>
 */
@Service
public class HealthMetricService {

    private final HealthMetricRepository metricRepository;

    public HealthMetricService(HealthMetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    @Transactional(readOnly = true)
    public List<HealthMetricResponse> list(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        return metricRepository.findByUserIdOrderByCreatedAtAsc(currentUser.getId()).stream()
                .map(HealthMetricResponse::from)
                .toList();
    }

    @Transactional
    public HealthMetricResponse create(String userId, HealthMetricRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new HealthValidationException("name is required");
        }
        if (request.type() == null) {
            throw new HealthValidationException("type is required");
        }
        validateTypePayload(request);

        HealthMetric metric = new HealthMetric();
        metric.setUserId(currentUser.getId());
        metric.setName(request.name());
        metric.setType(request.type());
        metric.setUnit(request.unit());
        metric.setColors(request.colors());
        metric.setLabels(request.labels());
        metric.setSeeded(false);

        if (request.type().isSingleSeries()) {
            metric.setData(request.data() == null ? new ArrayList<>() : new ArrayList<>(request.data()));
            metric.setDatasets(null);
        } else {
            List<Dataset> datasets = request.datasets() == null
                    ? new ArrayList<>()
                    : request.datasets().stream()
                            .map(HealthDatasetDto::toModel)
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            metric.setDatasets(datasets);
            metric.setData(null);
        }

        HealthMetric saved = metricRepository.save(metric);
        return HealthMetricResponse.from(saved);
    }

    @Transactional
    public void delete(String userId, String metricId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthMetric metric = metricRepository
                .findByIdAndUserId(metricId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Metric not found: " + metricId));
        if (metric.isSeeded()) {
            throw new HealthSeededMetricLockedException(
                    "Seeded metrics cannot be deleted");
        }
        metricRepository.delete(metric);
    }

    @Transactional
    public HealthMetricResponse appendPoint(
            String userId, String metricId, HealthMetricPointRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null) {
            throw new HealthValidationException("body is required");
        }
        HealthMetric metric = metricRepository
                .findByIdAndUserId(metricId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Metric not found: " + metricId));

        if (metric.getType().isSingleSeries()) {
            appendSingleSeriesPoint(metric, request);
        } else {
            appendMultiSeriesPoint(metric, request);
        }

        // Optional shared x-axis tick label; applies to both branches.
        if (request.label() != null) {
            List<String> labels = metric.getLabels() == null ? new ArrayList<>() : new ArrayList<>(metric.getLabels());
            labels.add(request.label());
            metric.setLabels(labels);
        }

        sortPointsByLabel(metric);

        HealthMetric saved = metricRepository.save(metric);
        return HealthMetricResponse.from(saved);
    }

    /**
     * Updates an existing metric's editable settings (name, unit, colors).
     * Type and stored series data are intentionally immutable here. Allowed on
     * seeded metrics — only whole-metric deletion is locked for those.
     */
    @Transactional
    public HealthMetricResponse updateMetric(
            String userId, String metricId, HealthMetricUpdateRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new HealthValidationException("name is required");
        }
        HealthMetric metric = metricRepository
                .findByIdAndUserId(metricId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Metric not found: " + metricId));

        metric.setName(request.name());
        metric.setUnit(request.unit());
        if (request.colors() != null) {
            metric.setColors(request.colors());
        }

        HealthMetric saved = metricRepository.save(metric);
        return HealthMetricResponse.from(saved);
    }

    /**
     * Replaces the value(s) — and optionally the label — of the point at {@code index}
     * (0-based, in label-sorted order). Re-sorts afterward in case the label changed.
     */
    @Transactional
    public HealthMetricResponse updatePoint(
            String userId, String metricId, int index, HealthMetricPointRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null) {
            throw new HealthValidationException("body is required");
        }
        HealthMetric metric = metricRepository
                .findByIdAndUserId(metricId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Metric not found: " + metricId));

        if (metric.getType().isSingleSeries()) {
            updateSingleSeriesPoint(metric, index, request);
        } else {
            updateMultiSeriesPoint(metric, index, request);
        }

        // Optional updated x-axis tick label for this point.
        if (request.label() != null) {
            List<String> labels = metric.getLabels() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(metric.getLabels());
            requireInBounds(index, labels.size());
            labels.set(index, request.label());
            metric.setLabels(labels);
        }

        sortPointsByLabel(metric);

        HealthMetric saved = metricRepository.save(metric);
        return HealthMetricResponse.from(saved);
    }

    /**
     * Removes the point at {@code index} (0-based, in label-sorted order) from the
     * label list and every parallel value series. Order is preserved, so no re-sort.
     */
    @Transactional
    public HealthMetricResponse deletePoint(
            String userId, String metricId, int index, User currentUser) {
        checkAccess(userId, currentUser);
        HealthMetric metric = metricRepository
                .findByIdAndUserId(metricId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Metric not found: " + metricId));

        if (metric.getType().isSingleSeries()) {
            List<BigDecimal> data = metric.getData() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(metric.getData());
            requireInBounds(index, data.size());
            data.remove(index);
            metric.setData(data);
        } else {
            List<Dataset> existing = metric.getDatasets() == null ? List.of() : metric.getDatasets();
            if (existing.isEmpty()) {
                throw new HealthValidationException("Metric has no datasets configured");
            }
            List<Dataset> updated = new ArrayList<>(existing.size());
            for (Dataset ds : existing) {
                List<BigDecimal> newData = ds.data() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(ds.data());
                requireInBounds(index, newData.size());
                newData.remove(index);
                updated.add(new Dataset(ds.label(), newData));
            }
            metric.setDatasets(updated);
        }

        // Keep the shared x-axis labels aligned with the trimmed series.
        if (metric.getLabels() != null && index >= 0 && index < metric.getLabels().size()) {
            List<String> labels = new ArrayList<>(metric.getLabels());
            labels.remove(index);
            metric.setLabels(labels);
        }

        HealthMetric saved = metricRepository.save(metric);
        return HealthMetricResponse.from(saved);
    }

    /**
     * Reorders the metric's parallel arrays ascending by label so the chart's
     * x-axis reflects the data point's date, not the order points were entered.
     * No-op when labels are absent, the parallel arrays disagree in length, or
     * the labels are already sorted.
     */
    private static void sortPointsByLabel(HealthMetric metric) {
        List<String> labels = metric.getLabels();
        if (labels == null || labels.size() < 2) {
            return;
        }
        int n = labels.size();
        if (metric.getType().isSingleSeries()) {
            List<BigDecimal> data = metric.getData();
            if (data == null || data.size() != n) return;
        } else {
            List<Dataset> datasets = metric.getDatasets();
            if (datasets == null) return;
            for (Dataset ds : datasets) {
                if (ds.data() == null || ds.data().size() != n) return;
            }
        }

        int[] order = HealthMetricPointSorter.sortIndicesByLabelAscending(labels);
        if (order == null) return;

        metric.setLabels(HealthMetricPointSorter.applyIndices(labels, order));
        if (metric.getType().isSingleSeries()) {
            metric.setData(HealthMetricPointSorter.applyIndices(metric.getData(), order));
        } else {
            List<Dataset> reordered = new ArrayList<>(metric.getDatasets().size());
            for (Dataset ds : metric.getDatasets()) {
                reordered.add(new Dataset(ds.label(), HealthMetricPointSorter.applyIndices(ds.data(), order)));
            }
            metric.setDatasets(reordered);
        }
    }

    private void appendSingleSeriesPoint(HealthMetric metric, HealthMetricPointRequest request) {
        if (request.value() == null) {
            throw new HealthValidationException(
                    "value is required for single-series (line, bar) metrics");
        }
        if (request.values() != null && !request.values().isEmpty()) {
            throw new HealthValidationException(
                    "Single-series metrics use 'value', not 'values'");
        }
        List<BigDecimal> data = metric.getData() == null
                ? new ArrayList<>()
                : new ArrayList<>(metric.getData());
        data.add(request.value());
        metric.setData(data);
    }

    private void appendMultiSeriesPoint(HealthMetric metric, HealthMetricPointRequest request) {
        List<Dataset> existing = metric.getDatasets();
        java.util.Map<String, BigDecimal> incoming = mapAndValidateMultiSeriesValues(existing, request);

        // Append one value per dataset, preserving original dataset order.
        List<Dataset> updated = new ArrayList<>(existing.size());
        for (Dataset ds : existing) {
            List<BigDecimal> newData = ds.data() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(ds.data());
            newData.add(incoming.get(ds.label()));
            updated.add(new Dataset(ds.label(), newData));
        }
        metric.setDatasets(updated);
    }

    private void updateSingleSeriesPoint(HealthMetric metric, int index, HealthMetricPointRequest request) {
        if (request.value() == null) {
            throw new HealthValidationException(
                    "value is required for single-series (line, bar) metrics");
        }
        if (request.values() != null && !request.values().isEmpty()) {
            throw new HealthValidationException(
                    "Single-series metrics use 'value', not 'values'");
        }
        List<BigDecimal> data = metric.getData() == null
                ? new ArrayList<>()
                : new ArrayList<>(metric.getData());
        requireInBounds(index, data.size());
        data.set(index, request.value());
        metric.setData(data);
    }

    private void updateMultiSeriesPoint(HealthMetric metric, int index, HealthMetricPointRequest request) {
        List<Dataset> existing = metric.getDatasets();
        java.util.Map<String, BigDecimal> incoming = mapAndValidateMultiSeriesValues(existing, request);

        List<Dataset> updated = new ArrayList<>(existing.size());
        for (Dataset ds : existing) {
            List<BigDecimal> newData = ds.data() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(ds.data());
            requireInBounds(index, newData.size());
            newData.set(index, incoming.get(ds.label()));
            updated.add(new Dataset(ds.label(), newData));
        }
        metric.setDatasets(updated);
    }

    /**
     * Validates a multi-series point payload against a metric's datasets and returns
     * an insertion-ordered label → value map. Shared by append and update so both
     * enforce the same single-vs-multi and exact-dataset-label-match rules.
     */
    private static java.util.Map<String, BigDecimal> mapAndValidateMultiSeriesValues(
            List<Dataset> existing, HealthMetricPointRequest request) {
        if (request.value() != null) {
            throw new HealthValidationException(
                    "Multi-series metrics use 'values', not 'value'");
        }
        if (request.values() == null || request.values().isEmpty()) {
            throw new HealthValidationException(
                    "values is required for multi-series (dual-line, multi-line) metrics");
        }
        if (existing == null || existing.isEmpty()) {
            throw new HealthValidationException(
                    "Metric has no datasets configured; cannot set a multi-series point");
        }

        // Build label → value map from incoming, rejecting duplicates.
        java.util.Map<String, BigDecimal> incoming = new java.util.LinkedHashMap<>();
        for (HealthMetricPointRequest.MultiSeriesValue v : request.values()) {
            if (v == null || v.label() == null || v.label().isBlank() || v.value() == null) {
                throw new HealthValidationException(
                        "every entry in 'values' requires a non-blank label and a value");
            }
            if (incoming.put(v.label(), v.value()) != null) {
                throw new HealthValidationException(
                        "duplicate dataset label in values: " + v.label());
            }
        }

        // Incoming label set must equal existing dataset label set exactly —
        // no missing datasets, no extras. Prevents history drift between series.
        java.util.Set<String> existingLabels = existing.stream()
                .map(Dataset::label)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!incoming.keySet().equals(existingLabels)) {
            throw new HealthValidationException(
                    "values labels must exactly match the metric's dataset labels "
                            + existingLabels + " (received " + incoming.keySet() + ")");
        }
        return incoming;
    }

    private static void requireInBounds(int index, int size) {
        if (index < 0 || index >= size) {
            throw new HealthValidationException("point index out of range: " + index);
        }
    }

    /**
     * Enforces the single-series vs multi-series payload invariant.
     */
    private void validateTypePayload(HealthMetricRequest request) {
        if (request.type().isSingleSeries()) {
            if (request.datasets() != null && !request.datasets().isEmpty()) {
                throw new HealthValidationException(
                        "Single-series metrics (line, bar) must use 'data', not 'datasets'");
            }
        } else {
            if (request.data() != null && !request.data().isEmpty()) {
                throw new HealthValidationException(
                        "Multi-series metrics (dual-line, multi-line) must use 'datasets', not 'data'");
            }
        }
    }

    private void checkAccess(String userId, User currentUser) {
        if (!currentUser.getUsername().equals(userId)) {
            throw new HealthAccessDeniedException(
                    "Access denied: you can only access your own health data");
        }
    }
}
