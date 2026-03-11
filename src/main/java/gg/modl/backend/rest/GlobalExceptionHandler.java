package gg.modl.backend.rest;

import gg.modl.backend.settings.service.SettingsConflictException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String INVALID_DATA_MESSAGE = "Invalid data provided.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        return invalidDataResponse();
    }

    private ResponseEntity<Map<String, Object>> invalidDataResponse() {
        return ResponseEntity.badRequest().body(Map.of(
            "status", 400,
            "error", INVALID_DATA_MESSAGE
        ));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return invalidDataResponse();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return invalidDataResponse();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
            "status", 400,
            "error", ex.getMessage() != null ? ex.getMessage() : "Invalid argument"
        ));
    }

    @ExceptionHandler(SettingsConflictException.class)
    public ResponseEntity<Map<String, Object>> handleSettingsConflict(SettingsConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "status", 409,
            "error", ex.getMessage(),
            "currentVersion", ex.getCurrentVersion()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "success", false,
            "error", "An internal error occurred"
        ));
    }
}
