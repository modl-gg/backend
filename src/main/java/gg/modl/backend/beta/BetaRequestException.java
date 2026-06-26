package gg.modl.backend.beta;

import org.springframework.http.HttpStatus;

public class BetaRequestException extends RuntimeException {
    private final HttpStatus status;

    public BetaRequestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
