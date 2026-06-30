package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest.MultiSeriesValue;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricResponse;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricUpdateRequest;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HealthMetricService#updatePoint}, {@link HealthMetricService#deletePoint},
 * and {@link HealthMetricService#updateMetric}. Mirrors the mock-repo harness of
 * {@link HealthMetricAppendPointTest}.
 */
class HealthMetricUpdateDeleteTest {

    private static final String OWNER_USERNAME = "owner";
    private static final Long OWNER_ID = 42L;

    // ---- updatePoint: single-series ----

    @Test
    void updatePoint_singleSeries_replacesValueAtIndex() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100"), new BigDecimal("110")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("105"), null, "2025-05-10");

        HealthMetricResponse out = service.updatePoint(OWNER_USERNAME, "m1", 0, req, owner());

        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("105", "110");
        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-12");
    }

    @Test
    void updatePoint_singleSeries_nullLabel_keepsLabels() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100"), new BigDecimal("110")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        // No label in the payload — only the value changes.
        HealthMetricPointRequest req = new HealthMetricPointRequest(new BigDecimal("99"), null, null);

        HealthMetricResponse out = service.updatePoint(OWNER_USERNAME, "m1", 1, req, owner());

        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("100", "99");
        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-12");
    }

    @Test
    void updatePoint_singleSeries_changingLabel_reSortsAscending() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100"), new BigDecimal("110")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        // Backdate the second point so it should sort to the front.
        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("99"), null, "2025-05-09");

        HealthMetricResponse out = service.updatePoint(OWNER_USERNAME, "m1", 1, req, owner());

        assertThat(out.labels()).containsExactly("2025-05-09", "2025-05-10");
        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("99", "100");
    }

    @Test
    void updatePoint_singleSeries_outOfRangeIndex_isRejected() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(new BigDecimal("105"), null, "2025-05-11");

        assertThatThrownBy(() -> service.updatePoint(OWNER_USERNAME, "m1", 5, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void updatePoint_singleSeries_withValuesField_isRejected() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10")));
        HealthMetricService service = serviceFor(metric);

        // value AND values both present → single-series rejects 'values'.
        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("105"),
                List.of(new MultiSeriesValue("Systolic", new BigDecimal("120"))),
                "2025-05-10");

        assertThatThrownBy(() -> service.updatePoint(OWNER_USERNAME, "m1", 0, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("Single-series");
    }

    // ---- updatePoint: multi-series ----

    @Test
    void updatePoint_multiSeries_replacesAllSeriesAtIndex() {
        HealthMetric metric = bpMetric(
                new ArrayList<>(List.of(new BigDecimal("118"), new BigDecimal("122"))),
                new ArrayList<>(List.of(new BigDecimal("78"), new BigDecimal("82"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Diastolic", new BigDecimal("80"))),
                "2025-05-10");

        HealthMetricResponse out = service.updatePoint(OWNER_USERNAME, "m1", 0, req, owner());

        assertThat(out.datasets().get(0).label()).isEqualTo("Systolic");
        assertThat(out.datasets().get(0).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("120", "122");
        assertThat(out.datasets().get(1).label()).isEqualTo("Diastolic");
        assertThat(out.datasets().get(1).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("80", "82");
    }

    @Test
    void updatePoint_multiSeries_missingDatasetLabel_isRejected() {
        HealthMetric metric = bpMetric(
                new ArrayList<>(List.of(new BigDecimal("118"))),
                new ArrayList<>(List.of(new BigDecimal("78"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(new MultiSeriesValue("Systolic", new BigDecimal("120"))),
                "2025-05-10");

        assertThatThrownBy(() -> service.updatePoint(OWNER_USERNAME, "m1", 0, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("must exactly match");
    }

    @Test
    void updatePoint_multiSeries_outOfRangeIndex_isRejected() {
        HealthMetric metric = bpMetric(
                new ArrayList<>(List.of(new BigDecimal("118"))),
                new ArrayList<>(List.of(new BigDecimal("78"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Diastolic", new BigDecimal("80"))),
                "2025-05-10");

        assertThatThrownBy(() -> service.updatePoint(OWNER_USERNAME, "m1", 3, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("out of range");
    }

    // ---- deletePoint ----

    @Test
    void deletePoint_singleSeries_removesAtIndex() {
        HealthMetric metric = lineMetric(new ArrayList<>(List.of(
                new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("110"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-11", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricResponse out = service.deletePoint(OWNER_USERNAME, "m1", 1, owner());

        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("100", "110");
        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-12");
    }

    @Test
    void deletePoint_multiSeries_removesFromAllSeries() {
        HealthMetric metric = bpMetric(
                new ArrayList<>(List.of(new BigDecimal("118"), new BigDecimal("120"), new BigDecimal("122"))),
                new ArrayList<>(List.of(new BigDecimal("78"), new BigDecimal("80"), new BigDecimal("82"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-11", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricResponse out = service.deletePoint(OWNER_USERNAME, "m1", 1, owner());

        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-12");
        assertThat(out.datasets().get(0).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("118", "122");
        assertThat(out.datasets().get(1).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("78", "82");
    }

    @Test
    void deletePoint_outOfRangeIndex_isRejected() {
        HealthMetric metric = lineMetric(new ArrayList<>(List.of(new BigDecimal("100"))));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10")));
        HealthMetricService service = serviceFor(metric);

        assertThatThrownBy(() -> service.deletePoint(OWNER_USERNAME, "m1", 2, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("out of range");
    }

    // ---- updateMetric ----

    @Test
    void updateMetric_setsNameUnitAndColors() {
        HealthMetric metric = lineMetric(new ArrayList<>(List.of(new BigDecimal("100"))));
        metric.setName("Glucose");
        metric.setUnit("mg/dL");
        metric.setColors(new ArrayList<>(List.of("#00e5ff")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricUpdateRequest req = new HealthMetricUpdateRequest(
                "Fasting Glucose", "mmol/L", List.of("#ff0000"));

        HealthMetricResponse out = service.updateMetric(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.name()).isEqualTo("Fasting Glucose");
        assertThat(out.unit()).isEqualTo("mmol/L");
        assertThat(out.colors()).containsExactly("#ff0000");
        // Data is untouched by a settings update.
        assertThat(out.data()).extracting(BigDecimal::toPlainString).containsExactly("100");
    }

    @Test
    void updateMetric_blankName_isRejected() {
        HealthMetric metric = lineMetric(new ArrayList<>(List.of(new BigDecimal("100"))));
        HealthMetricService service = serviceFor(metric);

        HealthMetricUpdateRequest req = new HealthMetricUpdateRequest("   ", "kg", List.of("#fff"));

        assertThatThrownBy(() -> service.updateMetric(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void updateMetric_notFound_isRejected() {
        HealthMetricService service = serviceForMissing();

        HealthMetricUpdateRequest req = new HealthMetricUpdateRequest("New Name", "kg", List.of("#fff"));

        assertThatThrownBy(() -> service.updateMetric(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthNotFoundException.class)
                .hasMessageContaining("Metric not found");
    }

    // ---- helpers ----

    @SuppressWarnings("null")
    private static HealthMetricService serviceFor(HealthMetric metric) {
        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        when(repo.findByIdAndUserId("m1", OWNER_ID)).thenReturn(Optional.of(metric));
        when(repo.save(any(HealthMetric.class))).thenAnswer(inv -> Objects.requireNonNull(inv.getArgument(0)));
        return new HealthMetricService(repo);
    }

    private static HealthMetricService serviceForMissing() {
        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        when(repo.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        return new HealthMetricService(repo);
    }

    private static HealthMetric lineMetric(List<BigDecimal> existing) {
        HealthMetric m = new HealthMetric();
        m.setUserId(OWNER_ID);
        m.setName("Glucose");
        m.setType(HealthMetricType.LINE);
        m.setData(new ArrayList<>(existing));
        m.setDatasets(null);
        return m;
    }

    private static HealthMetric bpMetric(List<BigDecimal> systolic, List<BigDecimal> diastolic) {
        HealthMetric m = new HealthMetric();
        m.setUserId(OWNER_ID);
        m.setName("Blood Pressure");
        m.setType(HealthMetricType.DUAL_LINE);
        m.setData(null);
        List<Dataset> datasets = new ArrayList<>();
        datasets.add(new Dataset("Systolic", new ArrayList<>(systolic)));
        datasets.add(new Dataset("Diastolic", new ArrayList<>(diastolic)));
        m.setDatasets(datasets);
        return m;
    }

    private static User owner() {
        User u = new User(OWNER_USERNAME, "x", new java.util.HashSet<>(Set.of("ROLE_USER")));
        u.setId(OWNER_ID);
        return u;
    }
}
