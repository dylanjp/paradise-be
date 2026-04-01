package com.dylanjohnpratt.paradise.be.exception;

import com.dylanjohnpratt.paradise.be.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for Documentation Server related exceptions.
 * Maps each custom exception to its appropriate HTTP status code and error response.
 */
@RestControllerAdvice
public class DocsExceptionHandler {

    @ExceptionHandler(DocsPathTraversalException.class)
    public ResponseEntity<ErrorResponse> handlePathTraversal(DocsPathTraversalException ex) {
        ErrorResponse error = new ErrorResponse("DOCS_PATH_TRAVERSAL", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DocsFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(DocsFileNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("DOCS_FILE_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DocsInvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(DocsInvalidFileTypeException ex) {
        ErrorResponse error = new ErrorResponse("DOCS_INVALID_FILE_TYPE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
