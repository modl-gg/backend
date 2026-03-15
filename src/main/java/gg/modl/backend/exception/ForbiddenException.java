package gg.modl.backend.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseApplicationException {
    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, HttpStatus.FORBIDDEN, cause);
    }
}
