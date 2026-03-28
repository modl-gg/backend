package gg.modl.backend.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseApplicationException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, cause);
    }
}
