package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a file or folder with the same name already exists in the target directory.
 */
public class DriveItemConflictException extends RuntimeException {

    public DriveItemConflictException(String message) {
        super(message);
    }
}
