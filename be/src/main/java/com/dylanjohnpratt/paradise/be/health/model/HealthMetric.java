package com.dylanjohnpratt.paradise.be.health.model;

import com.dylanjohnpratt.paradise.be.health.model.converter.BigDecimalListJsonConverter;
import com.dylanjohnpratt.paradise.be.health.model.converter.DatasetListJsonConverter;
import com.dylanjohnpratt.paradise.be.health.model.converter.StringListJsonConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A per-user health metric (chart). Seeded metrics share the same slug across all users;
 * user-created metrics have a null slug.
 */
@Entity
@Table(
        name = "health_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_health_metric_user_slug",
                columnNames = {"user_id", "slug"}
        ),
        indexes = {
                @Index(name = "idx_health_metric_user", columnList = "user_id")
        }
)
public class HealthMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Seed slug (e.g. "bp", "glucose"). Null for user-created metrics. */
    @Column(name = "slug", length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private HealthMetricType type;

    @Column(name = "unit", length = 64)
    private String unit;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "colors", columnDefinition = "TEXT")
    private List<String> colors;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "labels", columnDefinition = "TEXT")
    private List<String> labels;

    /** Used when type is LINE or BAR; null for multi-series types. */
    @Convert(converter = BigDecimalListJsonConverter.class)
    @Column(name = "data", columnDefinition = "TEXT")
    private List<BigDecimal> data;

    /** Used when type is DUAL_LINE or MULTI_LINE; null for single-series types. */
    @Convert(converter = DatasetListJsonConverter.class)
    @Column(name = "datasets", columnDefinition = "TEXT")
    private List<Dataset> datasets;

    /** True for metrics seeded at user creation; protected from deletion. */
    @Column(name = "seeded", nullable = false)
    private boolean seeded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public HealthMetric() {
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HealthMetricType getType() {
        return type;
    }

    public void setType(HealthMetricType type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<BigDecimal> getData() {
        return data;
    }

    public void setData(List<BigDecimal> data) {
        this.data = data;
    }

    public List<Dataset> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<Dataset> datasets) {
        this.datasets = datasets;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
