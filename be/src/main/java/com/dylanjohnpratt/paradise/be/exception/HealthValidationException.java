package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a health request fails business-level validation
 * (e.g. payload shape mismatch, missing file, invalid type/payload combination).
 */
public class HealthValidationException extends RuntimeException {

    public HealthValidationException(String message) {
        super(message);
    }
}
