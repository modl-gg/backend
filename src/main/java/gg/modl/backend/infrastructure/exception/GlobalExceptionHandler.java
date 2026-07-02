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
public class GlobalExceptionHandler {
    private static final String INVALID_DATA_MESSAGE = "Invalid data provided.";
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;

    public GlobalExceptionHandler() {
        this(new ProtobufErrorResponseWriter());
    }

    public GlobalExceptionHandler(ProtobufErrorResponseWriter protobufErrorResponseWriter) {
        this.protobufErrorResponseWriter = protobufErrorResponseWriter;
    }

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
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(ex.getStatus(), machineCodeForStatus(ex.getStatus()), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponseDTO(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        log.debug("Duplicate key violation for {} {}", request.getMethod(), request.getRequestURI());
        String message = "The request conflicts with an existing record.";
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.CONFLICT, "CONFLICT", message);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(409, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Unhandled IllegalArgumentException — consider replacing with a typed exception at the throw site", ex);
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Invalid argument");
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, "Invalid argument"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(
        MissingServletRequestParameterException ex,
        HttpServletRequest request
    ) {
        String message = "Missing required parameter: " + ex.getParameterName();
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter: " + ex.getName();
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDTO(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleHandlerMethodValidation(
        HandlerMethodValidationException ex,
        HttpServletRequest request
    ) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", INVALID_DATA_MESSAGE);
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, INVALID_DATA_MESSAGE));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type");
        }
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new ErrorResponseDTO(415, "Unsupported media type"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<?> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Not acceptable");
        }
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
            .body(new ErrorResponseDTO(406, "Not acceptable"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request
    ) {
        String message = "HTTP method not supported: " + ex.getMethod();
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(new ErrorResponseDTO(405, message));
    }

    // Inactive under the default static-resource mapping (Spring MVC raises
    // NoResourceFoundException, handled below). Kept because it remains the correct handler if
    // spring.mvc.throw-exception-if-no-handler-found=true is ever enabled.
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        String message = "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL();
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDTO(404, message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("No resource found for {} {}", request.getMethod(), ex.getResourcePath());
        }
        String message = "No endpoint found for " + request.getMethod() + " " + request.getRequestURI();
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponseDTO(404, message));
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
            String code = machineCodeForStatus(status);
            String message = status.is4xxClientError() ? status.getReasonPhrase() : "An internal error occurred";
            if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
                return protobufError(status, code, message);
            }
            return ResponseEntity.status(status).body(new ErrorResponseDTO(status.value(), message));
        }

        log.error("Unhandled exception", ex);
        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return protobufError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "An internal error occurred");
        }
        return ResponseEntity.internalServerError()
            .body(new ErrorResponseDTO(500, "An internal error occurred"));
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

    private String machineCodeForStatus(HttpStatus status) {
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
}
