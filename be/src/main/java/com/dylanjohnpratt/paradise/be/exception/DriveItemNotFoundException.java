package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a referenced item ID does not correspond to an existing file or folder.
 */
public class DriveItemNotFoundException extends RuntimeException {

    public DriveItemNotFoundException(String message) {
        super(message);
    }
}
