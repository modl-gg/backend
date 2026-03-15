package gg.modl.backend.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BaseApplicationException {
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_REQUEST, cause);
    }
}
