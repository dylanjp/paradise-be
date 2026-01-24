package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when attempting to action a notification that has no action item.
 */
public class NoActionItemException extends RuntimeException {
    
    public NoActionItemException(String message) {
        super(message);
    }
    
    public NoActionItemException() {
        super("This notification does not have an action item");
    }
}
