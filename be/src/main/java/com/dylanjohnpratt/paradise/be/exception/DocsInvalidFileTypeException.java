package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a requested file is not a Markdown (.md) file.
 */
public class DocsInvalidFileTypeException extends RuntimeException {

    public DocsInvalidFileTypeException(String message) {
        super(message);
    }
}
