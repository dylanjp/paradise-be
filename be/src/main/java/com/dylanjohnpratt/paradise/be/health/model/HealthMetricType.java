package com.dylanjohnpratt.paradise.be.health.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthMetricType {
    LINE("line"),
    BAR("bar"),
    DUAL_LINE("dual-line"),
    MULTI_LINE("multi-line");

    private final String jsonValue;

    HealthMetricType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static HealthMetricType fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (HealthMetricType type : values()) {
            if (type.jsonValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown metric type: " + value);
    }

    public boolean isSingleSeries() {
        return this == LINE || this == BAR;
    }

    public boolean isMultiSeries() {
        return !isSingleSeries();
    }
}
