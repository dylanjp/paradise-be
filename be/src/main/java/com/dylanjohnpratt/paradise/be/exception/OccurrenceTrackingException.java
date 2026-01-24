package com.dylanjohnpratt.paradise.be.exception;

import java.time.LocalDate;

/**
 * Exception thrown when occurrence tracking fails for a notification.
 */
public class OccurrenceTrackingException extends RecurringActionTodoException {
    
    private final Long notificationId;
    private final LocalDate occurrenceDate;
    
    public OccurrenceTrackingException(Long notificationId, LocalDate occurrenceDate, 
                                        Throwable cause) {
        super("Failed to track occurrence for notification " + notificationId + 
              " on " + occurrenceDate, cause);
        this.notificationId = notificationId;
        this.occurrenceDate = occurrenceDate;
    }
    
    public Long getNotificationId() {
        return notificationId;
    }
    
    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }
}
