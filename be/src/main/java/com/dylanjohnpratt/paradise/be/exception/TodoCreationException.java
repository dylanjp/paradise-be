package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when TODO task creation fails for a specific notification and user.
 */
public class TodoCreationException extends RecurringActionTodoException {
    
    private final Long notificationId;
    private final Long userId;
    
    public TodoCreationException(Long notificationId, Long userId, Throwable cause) {
        super("Failed to create TODO for notification " + notificationId + 
              " and user " + userId, cause);
        this.notificationId = notificationId;
        this.userId = userId;
    }
    
    public Long getNotificationId() {
        return notificationId;
    }
    
    public Long getUserId() {
        return userId;
    }
}
