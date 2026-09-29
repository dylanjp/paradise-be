package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a requested text document exceeds the size served by {@code /docs/file}.
 */
public class DocsFileTooLargeException extends RuntimeException {

    public DocsFileTooLargeException(String message) {
        super(message);
    }
}
