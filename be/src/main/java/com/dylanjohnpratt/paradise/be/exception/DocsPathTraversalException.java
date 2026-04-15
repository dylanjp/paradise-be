package com.dylanjohnpratt.paradise.be.exception;

/**
 * Exception thrown when a requested path resolves outside the documentation root directory.
 */
public class DocsPathTraversalException extends RuntimeException {

    public DocsPathTraversalException(String message) {
        super(message);
    }
}
