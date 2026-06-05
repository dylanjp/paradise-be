package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricPointRequest.MultiSeriesValue;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricResponse;
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
 * Tests for {@link HealthMetricService#appendPoint} covering both branches:
 * single-series (legacy {@code value}) and multi-series (new {@code values}).
 */
class HealthMetricAppendPointTest {

    private static final String OWNER_USERNAME = "owner";
    private static final Long OWNER_ID = 42L;

    @Test
    void singleSeries_appendsValueToData() {
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("110"), null, "2025-05-14");

        HealthMetricResponse out = service.appendPoint(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("100", "110");
        assertThat(out.labels()).containsExactly("2025-05-14");
    }

    @Test
    void singleSeries_withValuesField_isRejected() {
        HealthMetric metric = lineMetric(List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("110"),
                List.of(new MultiSeriesValue("Systolic", new BigDecimal("120"))),
                null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("'value'");
    }

    @Test
    void multiSeries_appendsOneValuePerDataset() {
        HealthMetric metric = bpMetric(
                List.of(new BigDecimal("118")),  // Systolic history
                List.of(new BigDecimal("78"))    // Diastolic history
        );
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Diastolic", new BigDecimal("80"))
                ),
                "2025-05-14");

        HealthMetricResponse out = service.appendPoint(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.datasets()).hasSize(2);
        assertThat(out.datasets().get(0).label()).isEqualTo("Systolic");
        assertThat(out.datasets().get(0).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("118", "120");
        assertThat(out.datasets().get(1).label()).isEqualTo("Diastolic");
        assertThat(out.datasets().get(1).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("78", "80");
        assertThat(out.labels()).containsExactly("2025-05-14");
    }

    @Test
    void multiSeries_orderOfValuesIrrelevant() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        // Diastolic first in payload — should still land in the right dataset.
        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Diastolic", new BigDecimal("80")),
                        new MultiSeriesValue("Systolic", new BigDecimal("120"))
                ),
                null);

        HealthMetricResponse out = service.appendPoint(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.datasets().get(0).label()).isEqualTo("Systolic");
        assertThat(out.datasets().get(0).data().get(0)).isEqualByComparingTo("120");
        assertThat(out.datasets().get(1).label()).isEqualTo("Diastolic");
        assertThat(out.datasets().get(1).data().get(0)).isEqualByComparingTo("80");
    }

    @Test
    void multiSeries_missingDatasetLabel_isRejected() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(new MultiSeriesValue("Systolic", new BigDecimal("120"))),
                null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("must exactly match");
    }

    @Test
    void multiSeries_extraDatasetLabel_isRejected() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Diastolic", new BigDecimal("80")),
                        new MultiSeriesValue("MeanArterial", new BigDecimal("93"))
                ),
                null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("must exactly match");
    }

    @Test
    void multiSeries_duplicateLabel_isRejected() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Systolic", new BigDecimal("121"))
                ),
                null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void multiSeries_withSingleValueField_isRejected() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("120"), null, null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("'values'");
    }

    @Test
    void singleSeries_outOfOrderDate_isSortedAscending() {
        // Existing history: two points dated 2025-05-10 and 2025-05-12 (entered chronologically).
        HealthMetric metric = lineMetric(List.of(new BigDecimal("100"), new BigDecimal("110")));
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        // User enters a backdated point — 2025-05-11 — after the others.
        HealthMetricPointRequest req = new HealthMetricPointRequest(
                new BigDecimal("105"), null, "2025-05-11");

        HealthMetricResponse out = service.appendPoint(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-11", "2025-05-12");
        assertThat(out.data()).extracting(BigDecimal::toPlainString)
                .containsExactly("100", "105", "110");
    }

    @Test
    void multiSeries_outOfOrderDate_sortsAllSeriesTogether() {
        HealthMetric metric = bpMetric(
                new ArrayList<>(List.of(new BigDecimal("118"), new BigDecimal("122"))),
                new ArrayList<>(List.of(new BigDecimal("78"), new BigDecimal("82")))
        );
        metric.setLabels(new ArrayList<>(List.of("2025-05-10", "2025-05-12")));
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(
                null,
                List.of(
                        new MultiSeriesValue("Systolic", new BigDecimal("120")),
                        new MultiSeriesValue("Diastolic", new BigDecimal("80"))
                ),
                "2025-05-11");

        HealthMetricResponse out = service.appendPoint(OWNER_USERNAME, "m1", req, owner());

        assertThat(out.labels()).containsExactly("2025-05-10", "2025-05-11", "2025-05-12");
        assertThat(out.datasets().get(0).label()).isEqualTo("Systolic");
        assertThat(out.datasets().get(0).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("118", "120", "122");
        assertThat(out.datasets().get(1).label()).isEqualTo("Diastolic");
        assertThat(out.datasets().get(1).data()).extracting(BigDecimal::toPlainString)
                .containsExactly("78", "80", "82");
    }

    @Test
    void multiSeries_emptyValues_isRejected() {
        HealthMetric metric = bpMetric(List.of(), List.of());
        HealthMetricService service = serviceFor(metric);

        HealthMetricPointRequest req = new HealthMetricPointRequest(null, List.of(), null);

        assertThatThrownBy(() -> service.appendPoint(OWNER_USERNAME, "m1", req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("values is required");
    }

    // ---- helpers ----

    @SuppressWarnings("null")
    private static HealthMetricService serviceFor(HealthMetric metric) {
        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        when(repo.findByIdAndUserId("m1", OWNER_ID)).thenReturn(Optional.of(metric));
        when(repo.save(any(HealthMetric.class))).thenAnswer(inv -> Objects.requireNonNull(inv.getArgument(0)));
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
