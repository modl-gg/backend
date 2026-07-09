package gg.modl.backend.beta;

import gg.modl.backend.infrastructure.exception.BaseApplicationException;
import org.springframework.http.HttpStatus;

public class BetaRequestException extends BaseApplicationException {
    public BetaRequestException(String message, HttpStatus status) {
        super(message, status);
    }
}
