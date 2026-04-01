package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a requested documentation file does not exist.
 */
public class DocsFileNotFoundException extends RuntimeException {

    public DocsFileNotFoundException(String message) {
        super(message);
    }
}
