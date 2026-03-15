package gg.modl.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseApplicationException extends RuntimeException {
    private final HttpStatus status;

    protected BaseApplicationException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected BaseApplicationException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
