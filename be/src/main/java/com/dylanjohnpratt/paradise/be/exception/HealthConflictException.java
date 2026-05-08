package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a health resource operation conflicts with existing data
 * (e.g. duplicate slug, concurrent modification).
 */
public class HealthConflictException extends RuntimeException {

    public HealthConflictException(String message) {
        super(message);
    }
}
