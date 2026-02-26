package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a drive path is inaccessible or auto-provisioning fails.
 */
public class DriveUnavailableException extends RuntimeException {

    public DriveUnavailableException(String message) {
        super(message);
    }
}
