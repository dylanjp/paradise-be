package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthDatasetDto;
import com.dylanjohnpratt.paradise.be.dto.HealthMetricRequest;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests pinning the type-vs-payload invariant on
 * {@link HealthMetricService#create}:
 * <ul>
 *   <li>Single-series types ({@code LINE}, {@code BAR}) rejected when {@code datasets} non-empty.</li>
 *   <li>Multi-series types ({@code DUAL_LINE}, {@code MULTI_LINE}) rejected when {@code data} non-empty.</li>
 * </ul>
 */
class HealthMetricServicePropertyTest {

    private static final String OWNER_USERNAME = "owner";

    @Property(tries = 100)
    void singleSeries_withDatasets_throws(
            @ForAll @From("singleSeries") HealthMetricType type,
            @ForAll @From("nonEmptyDatasets") List<HealthDatasetDto> datasets) {

        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        HealthMetricService service = new HealthMetricService(repo);

        HealthMetricRequest req = new HealthMetricRequest(
                "m", type, null, null, null, null, datasets);

        assertThatThrownBy(() -> service.create(OWNER_USERNAME, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("'data'");
    }

    @Property(tries = 100)
    void multiSeries_withData_throws(
            @ForAll @From("multiSeries") HealthMetricType type,
            @ForAll @From("nonEmptyData") List<BigDecimal> data) {

        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        HealthMetricService service = new HealthMetricService(repo);

        HealthMetricRequest req = new HealthMetricRequest(
                "m", type, null, null, null, data, null);

        assertThatThrownBy(() -> service.create(OWNER_USERNAME, req, owner()))
                .isInstanceOf(HealthValidationException.class)
                .hasMessageContaining("'datasets'");
    }

    @SuppressWarnings("null")
    @Property(tries = 100)
    void singleSeries_withData_succeeds(
            @ForAll @From("singleSeries") HealthMetricType type,
            @ForAll @From("anyData") List<BigDecimal> data) {

        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        when(repo.save(any(HealthMetric.class))).thenAnswer(inv -> Objects.requireNonNull(inv.getArgument(0)));
        HealthMetricService service = new HealthMetricService(repo);

        HealthMetricRequest req = new HealthMetricRequest(
                "m", type, null, null, null, data, null);

        // Should not throw.
        service.create(OWNER_USERNAME, req, owner());
    }

    @SuppressWarnings("null")
    @Property(tries = 100)
    void multiSeries_withDatasets_succeeds(
            @ForAll @From("multiSeries") HealthMetricType type,
            @ForAll @From("anyDatasets") List<HealthDatasetDto> datasets) {

        HealthMetricRepository repo = mock(HealthMetricRepository.class);
        when(repo.save(any(HealthMetric.class))).thenAnswer(inv -> Objects.requireNonNull(inv.getArgument(0)));
        HealthMetricService service = new HealthMetricService(repo);

        HealthMetricRequest req = new HealthMetricRequest(
                "m", type, null, null, null, null, datasets);

        // Should not throw.
        service.create(OWNER_USERNAME, req, owner());
    }

    // ---- generators ----

    @Provide
    Arbitrary<HealthMetricType> singleSeries() {
        return Arbitraries.of(HealthMetricType.LINE, HealthMetricType.BAR);
    }

    @Provide
    Arbitrary<HealthMetricType> multiSeries() {
        return Arbitraries.of(HealthMetricType.DUAL_LINE, HealthMetricType.MULTI_LINE);
    }

    @Provide
    Arbitrary<List<BigDecimal>> anyData() {
        return Arbitraries.integers().between(0, 1000)
                .map(i -> BigDecimal.valueOf(i.longValue()))
                .list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<BigDecimal>> nonEmptyData() {
        return Arbitraries.integers().between(0, 1000)
                .map(i -> BigDecimal.valueOf(i.longValue()))
                .list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<HealthDatasetDto>> anyDatasets() {
        Arbitrary<HealthDatasetDto> single = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)
                .map(label -> new HealthDatasetDto(label, List.of(BigDecimal.ONE)));
        return single.list().ofMinSize(0).ofMaxSize(4);
    }

    @Provide
    Arbitrary<List<HealthDatasetDto>> nonEmptyDatasets() {
        Arbitrary<HealthDatasetDto> single = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)
                .map(label -> new HealthDatasetDto(label, List.of(BigDecimal.ONE)));
        return single.list().ofMinSize(1).ofMaxSize(4);
    }

    // ---- fixtures ----

    private static User owner() {
        User u = new User(OWNER_USERNAME, "x", new HashSet<>(Set.of("ROLE_USER")));
        u.setId(42L);
        return u;
    }
}
