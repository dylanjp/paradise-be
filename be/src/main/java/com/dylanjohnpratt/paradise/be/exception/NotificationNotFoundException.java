package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a notification is not found or not accessible to the user.
 */
public class NotificationNotFoundException extends RuntimeException {
    
    public NotificationNotFoundException(String message) {
        super(message);
    }
    
    public NotificationNotFoundException(Long notificationId) {
        super("Notification not found: " + notificationId);
    }
}
