package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a user does not have permission to access a drive.
 */
public class DriveAccessDeniedException extends RuntimeException {

    public DriveAccessDeniedException(String message) {
        super(message);
    }
}
