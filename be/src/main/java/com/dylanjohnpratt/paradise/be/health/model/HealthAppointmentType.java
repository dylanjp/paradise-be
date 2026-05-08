package com.dylanjohnpratt.paradise.be.health.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthAppointmentType {
    UPCOMING("upcoming"),
    VISITED("visited");

    private final String jsonValue;

    HealthAppointmentType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static HealthAppointmentType fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (HealthAppointmentType type : values()) {
            if (type.jsonValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown appointment type: " + value);
    }
}
