package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a user attempts to action a notification they have already actioned.
 */
public class DuplicateActionException extends RuntimeException {
    
    public DuplicateActionException(String message) {
        super(message);
    }
    
    public DuplicateActionException() {
        super("You have already created a task from this notification");
    }
}
