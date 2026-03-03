package gg.modl.backend.rest;

import gg.modl.backend.settings.service.SettingsConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        final List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Invalid data provided",
                "errors", errors
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Malformed request body"
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
}
