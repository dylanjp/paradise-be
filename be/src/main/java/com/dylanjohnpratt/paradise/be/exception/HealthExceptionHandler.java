package com.dylanjohnpratt.paradise.be.exception;

import com.dylanjohnpratt.paradise.be.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Exception handler for Health feature endpoints.
 * <p>
 * Scoped via {@code basePackages} so its {@link MethodArgumentNotValidException}
 * handler cannot swallow validation errors from other modules (notifications,
 * drive, docs, etc.). Each handler returns the standard
 * {@link ErrorResponse} shape; validation errors are joined into the
 * {@code message} field as {@code "field1: msg, field2: msg"}.
 */
@RestControllerAdvice(basePackages = "com.dylanjohnpratt.paradise.be.health.controller")
public class HealthExceptionHandler {

    @ExceptionHandler(HealthAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HealthAccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_ACCESS_DENIED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(HealthNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(HealthNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(HealthConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(HealthConflictException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_CONFLICT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(HealthValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(HealthValidationException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_VALIDATION_FAILED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        ErrorResponse error = new ErrorResponse("HEALTH_VALIDATION_FAILED", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HealthDocumentTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleDocumentTooLarge(HealthDocumentTooLargeException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_DOC_TOO_LARGE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(HealthDocumentUnsupportedException.class)
    public ResponseEntity<ErrorResponse> handleDocumentUnsupported(HealthDocumentUnsupportedException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_DOC_UNSUPPORTED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    }

    @ExceptionHandler(HealthSeededMetricLockedException.class)
    public ResponseEntity<ErrorResponse> handleSeededMetricLocked(HealthSeededMetricLockedException ex) {
        ErrorResponse error = new ErrorResponse("HEALTH_SEEDED_METRIC_LOCKED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return fieldError.getField() + ": " + (defaultMessage != null ? defaultMessage : "invalid");
    }
}
