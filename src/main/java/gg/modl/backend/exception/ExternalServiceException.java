package gg.modl.backend.exception;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends BaseApplicationException {
    public ExternalServiceException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
