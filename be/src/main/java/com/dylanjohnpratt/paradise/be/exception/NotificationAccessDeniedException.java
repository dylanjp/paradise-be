package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a user attempts to access a notification they don't have permission for.
 */
public class NotificationAccessDeniedException extends RuntimeException {
    
    public NotificationAccessDeniedException(String message) {
        super(message);
    }
    
    public NotificationAccessDeniedException() {
        super("You do not have access to this notification");
    }
}
