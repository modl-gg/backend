package gg.modl.backend.infrastructure.exception;

import gg.modl.backend.infrastructure.proto.ProtoValidationException;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.backend.settings.service.SettingsConflictException;
import gg.modl.proto.modl.v1.ApiError;
import gg.modl.proto.modl.v1.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private static final String INVALID_DATA_MESSAGE = "Invalid data provided.";
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;

    @ExceptionHandler(SettingsConflictException.class)
    public ResponseEntity<?> handleSettingsConflict(SettingsConflictException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(ex.getStatus(), "CONFLICT", ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
            "status", ex.getStatus().value(),
            "error", ex.getMessage(),
            "message", ex.getMessage(),
            "currentVersion", ex.getCurrentVersion()
        ));
    }

    @ExceptionHandler(BaseApplicationException.class)
    public ResponseEntity<?> handleApplicationException(BaseApplicationException ex, HttpServletRequest request) {
        return respond(request, ex.getStatus(), HttpErrorMapping.machineCode(ex.getStatus()), ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        log.debug("Duplicate key violation for {} {}", request.getMethod(), request.getRequestURI());
        return respond(request, HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with an existing record.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Unhandled IllegalArgumentException. Consider replacing with a typed exception at the throw site", ex);
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Invalid argument");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(
        MissingServletRequestParameterException ex,
        HttpServletRequest request
    ) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Invalid value for parameter: " + ex.getName());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", fieldErrorMessage(ex));
    }

    private static String fieldErrorMessage(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        return details.isEmpty() ? INVALID_DATA_MESSAGE : "Invalid data provided: " + details + ".";
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleHandlerMethodValidation(
        HandlerMethodValidationException ex,
        HttpServletRequest request
    ) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<?> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Not acceptable");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request
    ) {
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported: " + ex.getMethod());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("No resource found for {} {}", request.getMethod(), ex.getResourcePath());
        }
        return respond(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint found for " + request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(ProtoValidationException.class)
    public ResponseEntity<?> handleProtoValidation(ProtoValidationException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            ApiError.Builder error = ApiError.newBuilder()
                .setStatusCode(400)
                .setCode("INVALID_ARGUMENT")
                .setMessage(ex.getMessage());
            ex.getViolations().stream()
                .map(violation -> FieldViolation.newBuilder()
                    .setField(Objects.toString(violation.getFieldPath(), ""))
                    .setMessage(violation.getMessage())
                    .build())
                .forEach(error::addFieldViolations);
            return protobufError(HttpStatus.BAD_REQUEST, error.build());
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (status.is4xxClientError()) {
                log.debug("Client error {} for {} {}", status.value(), request.getMethod(), request.getRequestURI());
            } else {
                log.error("Server error from framework exception", ex);
            }
            String code = HttpErrorMapping.machineCode(status);
            String message = status.is4xxClientError() ? status.getReasonPhrase() : "An internal error occurred";
            return respond(request, status, code, message);
        }

        log.error("Unhandled exception", ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "An internal error occurred");
    }

    private ResponseEntity<?> respond(HttpServletRequest request, HttpStatus status, String code, String message) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(status, code, message);
        }
        return ResponseEntity.status(status).body(new ErrorResponseDTO(status.value(), message));
    }

    private ResponseEntity<ApiError> protobufError(HttpStatus status, String code, String message) {
        return protobufError(status, ApiError.newBuilder()
            .setStatusCode(status.value())
            .setCode(code)
            .setMessage(message)
            .build());
    }

    private ResponseEntity<ApiError> protobufError(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status)
            .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
            .body(error);
    }
}
