package com.dylanjohnpratt.paradise.be.health.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthDocumentCategory {
    LAB_RESULTS("Lab Results"),
    BLOOD_TEST("Blood Test"),
    IMAGING("Imaging"),
    PRESCRIPTIONS("Prescriptions"),
    VACCINATION("Vaccination"),
    VISIT_NOTES("Visit Notes"),
    OTHER("Other");

    private final String displayName;

    HealthDocumentCategory(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static HealthDocumentCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (HealthDocumentCategory category : values()) {
            if (category.displayName.equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown document category: " + value);
    }
}
