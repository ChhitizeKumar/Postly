package com.postly.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    // Shortcuts
    public static AuthException badRequest(String message) {
        return new AuthException(message, HttpStatus.BAD_REQUEST);
    }

    public static AuthException unauthorized(String message) {
        return new AuthException(message, HttpStatus.UNAUTHORIZED);
    }

    public static AuthException notFound(String message) {
        return new AuthException(message, HttpStatus.NOT_FOUND);
    }

    public static AuthException conflict(String message) {
        return new AuthException(message, HttpStatus.CONFLICT);
    }
}
