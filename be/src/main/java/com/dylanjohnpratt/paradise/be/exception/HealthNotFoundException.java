package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a requested health resource does not exist for the authenticated user.
 */
public class HealthNotFoundException extends RuntimeException {

    public HealthNotFoundException(String message) {
        super(message);
    }
}
