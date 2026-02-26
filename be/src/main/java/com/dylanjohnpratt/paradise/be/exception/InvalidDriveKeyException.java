package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a request contains an invalid or unrecognized drive key.
 */
public class InvalidDriveKeyException extends RuntimeException {

    public InvalidDriveKeyException(String message) {
        super(message);
    }
}
