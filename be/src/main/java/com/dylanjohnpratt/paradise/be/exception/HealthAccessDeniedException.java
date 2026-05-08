package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a user attempts to access another user's health data.
 * Strict isolation — {@code ROLE_ADMIN} does not bypass this check.
 */
public class HealthAccessDeniedException extends RuntimeException {

    public HealthAccessDeniedException(String message) {
        super(message);
    }
}
