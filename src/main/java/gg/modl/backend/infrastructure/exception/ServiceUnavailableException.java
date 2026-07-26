package gg.modl.backend.infrastructure.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseApplicationException {
    public ServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
