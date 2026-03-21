package com.postly.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors (@Valid fails)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        return ResponseEntity.badRequest().body(Map.of(
                "status",    400,
                "error",     "Validation failed",
                "fields",    fieldErrors,
                "timestamp", Instant.now().toString()
        ));
    }

    // Business logic errors (email taken, wrong password etc)
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        log.warn("Auth error: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "status",    ex.getStatus().value(),
                "error",     ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status",    500,
                "error",     "Something went wrong",
                "timestamp", Instant.now().toString()
        ));
    }
}
