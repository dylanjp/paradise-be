package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when an attempt is made to delete the root entry of a drive.
 */
public class DriveRootDeletionException extends RuntimeException {

    public DriveRootDeletionException(String message) {
        super(message);
    }
}
