package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a user attempts to delete a seeded metric. Seeded metrics
 * are owned by the system and cannot be removed — only appended to or reset.
 */
public class HealthSeededMetricLockedException extends RuntimeException {

    public HealthSeededMetricLockedException(String message) {
        super(message);
    }
}
