package com.dylanjohnpratt.paradise.be.exception;

/**
 * Base exception for recurring action TODO processing errors.
 */
public class RecurringActionTodoException extends RuntimeException {
    
    public RecurringActionTodoException(String message) {
        super(message);
    }
    
    public RecurringActionTodoException(String message, Throwable cause) {
        super(message, cause);
    }
}
