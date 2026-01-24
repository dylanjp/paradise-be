package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when attempting to action an expired notification.
 */
public class NotificationExpiredException extends RuntimeException {
    
    public NotificationExpiredException(String message) {
        super(message);
    }
    
    public NotificationExpiredException() {
        super("Cannot action an expired notification");
    }
}
