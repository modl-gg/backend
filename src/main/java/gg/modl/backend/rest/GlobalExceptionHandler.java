package gg.modl.backend.rest;

import gg.modl.backend.exception.BaseApplicationException;
import gg.modl.backend.exception.ErrorResponseDTO;
import gg.modl.backend.settings.service.SettingsConflictException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(SettingsConflictException.class)
    public ResponseEntity<Map<String, Object>> handleSettingsConflict(SettingsConflictException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
            "status", ex.getStatus().value(),
            "error", ex.getMessage(),
            "currentVersion", ex.getCurrentVersion()
        ));
    }

    @ExceptionHandler(BaseApplicationException.class)
    public ResponseEntity<ErrorResponseDTO> handleApplicationException(BaseApplicationException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponseDTO(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Untyped IllegalArgumentException — should be replaced with a typed exception", ex);
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, ex.getMessage() != null ? ex.getMessage() : "Invalid argument"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponseDTO(500, "An internal error occurred"));
    }
}
