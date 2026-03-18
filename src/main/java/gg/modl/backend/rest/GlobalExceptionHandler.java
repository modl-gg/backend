package gg.modl.backend.rest;

import gg.modl.backend.exception.BaseApplicationException;
import gg.modl.backend.exception.ErrorResponseDTO;
import gg.modl.backend.settings.service.SettingsConflictException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

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
        log.warn("Unhandled IllegalArgumentException — consider replacing with a typed exception at the throw site", ex);
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, "Invalid argument"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, "Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, "Invalid value for parameter: " + ex.getName()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(new ErrorResponseDTO(405, "HTTP method not supported: " + ex.getMethod()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoHandlerFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDTO(404, "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponseDTO(500, "An internal error occurred"));
    }
}
