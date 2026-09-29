package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a requested file's extension is not served by the endpoint
 * (text: md, canvas; binary: pdf and the supported image types).
 */
public class DocsInvalidFileTypeException extends RuntimeException {

    public DocsInvalidFileTypeException(String message) {
        super(message);
    }
}
