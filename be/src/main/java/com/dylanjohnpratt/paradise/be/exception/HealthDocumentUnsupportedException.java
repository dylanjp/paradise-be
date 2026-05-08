package com.dylanjohnpratt.paradise.be.exception;

/**
 * Thrown when a health document upload uses a content type or extension
 * outside the allow-list (pdf, jpg, jpeg, png, docx, xlsx).
 */
public class HealthDocumentUnsupportedException extends RuntimeException {

    public HealthDocumentUnsupportedException(String message) {
        super(message);
    }
}
