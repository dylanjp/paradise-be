package com.dylanjohnpratt.paradise.be.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Record representing the result of processing recurring notifications.
 * Contains counts of processed notifications, created TODOs, errors, and error messages.
 */
public record ProcessingResult(
    int notificationsProcessed,
    int todosCreated,
    int errors,
    List<String> errorMessages
) {
    /**
     * Creates an empty ProcessingResult with all counts at zero.
     * 
     * @return an empty ProcessingResult
     */
    public static ProcessingResult empty() {
        return new ProcessingResult(0, 0, 0, List.of());
    }
    
    /**
     * Returns a new ProcessingResult with an additional notification processed.
     * 
     * @param todosCreated the number of TODOs created for this notification
     * @return a new ProcessingResult with updated counts
     */
    public ProcessingResult addNotification(int todosCreated) {
        return new ProcessingResult(
            this.notificationsProcessed + 1,
            this.todosCreated + todosCreated,
            this.errors,
            this.errorMessages
        );
    }
    
    /**
     * Returns a new ProcessingResult with an additional error recorded.
     * 
     * @param message the error message to add
     * @return a new ProcessingResult with updated error count and messages
     */
    public ProcessingResult addError(String message) {
        var newMessages = new ArrayList<>(this.errorMessages);
        newMessages.add(message);
        return new ProcessingResult(
            this.notificationsProcessed,
            this.todosCreated,
            this.errors + 1,
            newMessages
        );
    }
}
