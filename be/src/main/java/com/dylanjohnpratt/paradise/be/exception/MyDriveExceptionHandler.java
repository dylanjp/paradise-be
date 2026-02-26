package com.dylanjohnpratt.paradise.be.exception;

import com.dylanjohnpratt.paradise.be.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for My Drive related exceptions.
 * Maps each custom exception to its appropriate HTTP status code and error response.
 */
@RestControllerAdvice
public class MyDriveExceptionHandler {

    @ExceptionHandler(DriveAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDriveAccessDenied(DriveAccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse("DRIVE_ACCESS_DENIED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(InvalidDriveKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDriveKey(InvalidDriveKeyException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_DRIVE_KEY", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DriveItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDriveItemNotFound(DriveItemNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("DRIVE_ITEM_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DriveItemConflictException.class)
    public ResponseEntity<ErrorResponse> handleDriveItemConflict(DriveItemConflictException ex) {
        ErrorResponse error = new ErrorResponse("DRIVE_ITEM_CONFLICT", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DriveRootDeletionException.class)
    public ResponseEntity<ErrorResponse> handleDriveRootDeletion(DriveRootDeletionException ex) {
        ErrorResponse error = new ErrorResponse("DRIVE_ROOT_DELETION", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DownloadFolderException.class)
    public ResponseEntity<ErrorResponse> handleDownloadFolder(DownloadFolderException ex) {
        ErrorResponse error = new ErrorResponse("DOWNLOAD_FOLDER", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DriveUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDriveUnavailable(DriveUnavailableException ex) {
        ErrorResponse error = new ErrorResponse("DRIVE_UNAVAILABLE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}
