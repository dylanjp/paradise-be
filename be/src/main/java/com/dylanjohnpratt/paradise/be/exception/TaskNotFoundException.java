package com.dylanjohnpratt.paradise.be.exception;

/**
 * Runtime exception thrown when a task operation is attempted on a non-existent task
 * or a task that does not belong to the specified user.
 */
public class TaskNotFoundException extends RuntimeException {
    
    public TaskNotFoundException(String message) {
        super(message);
    }
}
