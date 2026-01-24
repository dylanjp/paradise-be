package com.dylanjohnpratt.paradise.be.exception;

import com.dylanjohnpratt.paradise.be.dto.ErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.persistence.PersistenceException;

/**
 * Global exception handler for application exceptions.
 * Maps exceptions to appropriate HTTP status codes and error responses.
 */
@ControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFound(TaskNotFoundException ex) {
        // "Access denied" messages should return 403, others return 404
        if (ex.getMessage() != null && ex.getMessage().contains("Access denied")) {
            ErrorResponse error = new ErrorResponse("FORBIDDEN", ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        ErrorResponse error = new ErrorResponse("TASK_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("NOTIFICATION_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(NotificationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(NotificationAccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse("FORBIDDEN", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(NotificationExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(NotificationExpiredException ex) {
        ErrorResponse error = new ErrorResponse("NOTIFICATION_EXPIRED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DuplicateActionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAction(DuplicateActionException ex) {
        ErrorResponse error = new ErrorResponse("ALREADY_ACTIONED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(NoActionItemException.class)
    public ResponseEntity<ErrorResponse> handleNoActionItem(NoActionItemException ex) {
        ErrorResponse error = new ErrorResponse("NO_ACTION_ITEM", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex) {
        String errorCode = determineValidationErrorCode(ex.getMessage());
        ErrorResponse error = new ErrorResponse(errorCode, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    private String determineValidationErrorCode(String message) {
        if (message == null) {
            return "VALIDATION_ERROR";
        }
        if (message.contains("Subject") && message.contains("255")) {
            return "INVALID_SUBJECT_LENGTH";
        }
        if (message.contains("Subject is required")) {
            return "EMPTY_SUBJECT";
        }
        if (message.contains("Message body is required")) {
            return "EMPTY_MESSAGE_BODY";
        }
        if (message.contains("Target user IDs required")) {
            return "MISSING_TARGET_USERS";
        }
        if (message.contains("day of week")) {
            return "INVALID_DAY_OF_WEEK";
        }
        if (message.contains("day of month")) {
            return "INVALID_DAY_OF_MONTH";
        }
        if (message.contains("recurrence type")) {
            return "INVALID_RECURRENCE_TYPE";
        }
        if (message.contains("Expiration") && (message.contains("future") || message.contains("past"))) {
            return "INVALID_EXPIRATION";
        }
        return "VALIDATION_ERROR";
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException ex) {
        ErrorResponse error = new ErrorResponse("DATABASE_ERROR", "A database error occurred: " + ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorResponse> handlePersistenceException(PersistenceException ex) {
        ErrorResponse error = new ErrorResponse("DATABASE_ERROR", "A database error occurred: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
