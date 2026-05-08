package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a health document upload exceeds the configured
 * {@code health.storage.max-bytes} limit.
 */
public class HealthDocumentTooLargeException extends RuntimeException {

    public HealthDocumentTooLargeException(String message) {
        super(message);
    }
}
