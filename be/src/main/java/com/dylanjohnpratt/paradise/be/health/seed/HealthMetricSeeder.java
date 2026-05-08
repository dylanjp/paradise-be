package com.dylanjohnpratt.paradise.be.health.seed;

import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetricType;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seeds the fixed set of built-in health metrics for a user. Idempotent by
 * {@code (userId, slug)} existence check, so calling this repeatedly — at
 * startup, on user creation, or during tests — is safe.
 */
@Component
public class HealthMetricSeeder {

    private static final Logger log = LoggerFactory.getLogger(HealthMetricSeeder.class);

    /** Canonical seed definitions. Matches {@code plan.md} seed data table. */
    static final List<SeedSpec> SEEDS = List.of(
            new SeedSpec(
                    "bp",
                    "Blood Pressure",
                    HealthMetricType.DUAL_LINE,
                    "mmHg",
                    List.of("#ef4444", "#3b82f6"),
                    () -> datasets(new Dataset("Systolic", emptyNumbers()),
                            new Dataset("Diastolic", emptyNumbers()))
            ),
            new SeedSpec(
                    "glucose",
                    "Glucose",
                    HealthMetricType.LINE,
                    "mg/dL",
                    List.of("#10b981"),
                    HealthMetricSeeder::emptyNumbers
            ),
            new SeedSpec(
                    "mood",
                    "Mood",
                    HealthMetricType.BAR,
                    null,
                    List.of("#f59e0b"),
                    HealthMetricSeeder::emptyNumbers
            ),
            new SeedSpec(
                    "weight",
                    "Weight",
                    HealthMetricType.LINE,
                    "lbs",
                    List.of("#06b6d4"),
                    HealthMetricSeeder::emptyNumbers
            ),
            new SeedSpec(
                    "testosterone",
                    "Testosterone",
                    HealthMetricType.LINE,
                    "ng/dL",
                    List.of("#8b5cf6"),
                    HealthMetricSeeder::emptyNumbers
            ),
            new SeedSpec(
                    "lipids",
                    "Lipids",
                    HealthMetricType.MULTI_LINE,
                    "mg/dL",
                    List.of("#ef4444", "#3b82f6", "#10b981", "#f59e0b"),
                    () -> datasets(
                            new Dataset("Total", emptyNumbers()),
                            new Dataset("LDL", emptyNumbers()),
                            new Dataset("HDL", emptyNumbers()),
                            new Dataset("Triglycerides", emptyNumbers()))
            )
    );

    private final HealthMetricRepository metricRepository;

    public HealthMetricSeeder(HealthMetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    /**
     * Ensures every canonical seed metric exists for {@code userId}.
     * Existing seeded rows are left untouched — their data is preserved.
     */
    @Transactional
    public void seedFor(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        int created = 0;
        for (SeedSpec spec : SEEDS) {
            if (metricRepository.existsByUserIdAndSlug(userId, spec.slug())) {
                continue;
            }
            HealthMetric metric = new HealthMetric();
            metric.setUserId(userId);
            metric.setSlug(spec.slug());
            metric.setName(spec.name());
            metric.setType(spec.type());
            metric.setUnit(spec.unit());
            metric.setColors(new ArrayList<>(spec.colors()));
            metric.setSeeded(true);
            Object payload = spec.payload().get();
            if (payload instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Dataset) {
                @SuppressWarnings("unchecked")
                List<Dataset> datasets = (List<Dataset>) list;
                metric.setDatasets(datasets);
                metric.setData(null);
            } else {
                @SuppressWarnings("unchecked")
                List<BigDecimal> data = (List<BigDecimal>) payload;
                metric.setData(data);
                metric.setDatasets(null);
            }
            metricRepository.save(metric);
            created++;
        }
        if (created > 0) {
            log.info("Seeded {} health metric(s) for user {}", created, userId);
        }
    }

    private static List<BigDecimal> emptyNumbers() {
        return new ArrayList<>();
    }

    private static List<Dataset> datasets(Dataset... items) {
        List<Dataset> list = new ArrayList<>(items.length);
        for (Dataset d : items) {
            list.add(d);
        }
        return list;
    }

    /**
     * Specification for one seeded metric row. {@code payload} produces either
     * a fresh empty {@code List<BigDecimal>} (single-series) or a fresh list of
     * empty-data {@link Dataset}s (multi-series).
     */
    record SeedSpec(
            String slug,
            String name,
            HealthMetricType type,
            String unit,
            List<String> colors,
            Supplier<Object> payload
    ) {}
}
