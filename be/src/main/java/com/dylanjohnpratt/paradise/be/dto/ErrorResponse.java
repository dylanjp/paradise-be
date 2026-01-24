package com.dylanjohnpratt.paradise.be.dto;

import java.time.LocalDateTime;

/**
 * Standard error response DTO for API error handling.
 */
public record ErrorResponse(
    String errorCode,
    String message,
    LocalDateTime timestamp
) {
    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, LocalDateTime.now());
    }
}
