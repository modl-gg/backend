package gg.modl.backend.infrastructure.proto;

import gg.modl.proto.modl.v1.ApiError;
import gg.modl.proto.modl.v1.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ProtoValidationSupport {
    private ProtoValidationSupport() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String optionalString(boolean present, String value) {
        return present ? value : null;
    }

    public static FieldViolation fieldViolation(String field, String message) {
        return FieldViolation.newBuilder()
            .setField(field)
            .setMessage(message)
            .build();
    }

    public static ResponseEntity<ApiError> validationError(List<FieldViolation> violations) {
        ApiError.Builder error = ApiError.newBuilder()
            .setStatusCode(HttpStatus.BAD_REQUEST.value())
            .setCode("INVALID_ARGUMENT")
            .setMessage("Invalid data provided.");
        violations.forEach(error::addFieldViolations);
        return ResponseEntity.badRequest()
            .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
            .body(error.build());
    }

    public static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
            .body(ApiError.newBuilder()
                .setStatusCode(status.value())
                .setCode(code)
                .setMessage(message)
                .build());
    }
}
