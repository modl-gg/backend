package gg.modl.backend.infrastructure.exception;

import org.springframework.http.HttpStatus;

public final class HttpErrorMapping {
    private HttpErrorMapping() {
    }

    public static String machineCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_ARGUMENT";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "PERMISSION_DENIED";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            default -> status.is5xxServerError() ? "INTERNAL" : status.name();
        };
    }

    public static String defaultMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "The requested resource was not found.";
            case FORBIDDEN -> "You do not have permission to access this resource.";
            case UNAUTHORIZED -> "Authentication is required to access this resource.";
            case INTERNAL_SERVER_ERROR -> "An internal server error occurred.";
            default -> status.getReasonPhrase();
        };
    }
}
