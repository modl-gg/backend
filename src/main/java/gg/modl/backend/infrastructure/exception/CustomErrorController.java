package gg.modl.backend.infrastructure.exception;

import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.proto.ProtobufMediaTypes;
import gg.modl.proto.modl.v1.ApiError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int statusCode = 500;
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString().trim());
            } catch (NumberFormatException ignored) {
                statusCode = 500;
            }
        }

        HttpStatus resolved = HttpStatus.resolve(statusCode);
        String error;
        String errorMessage;
        if (resolved != null) {
            error = resolved.getReasonPhrase();
            errorMessage = HttpErrorMapping.defaultMessage(resolved);
        } else {
            error = "Error";
            errorMessage = statusCode >= 500
                ? "An internal server error occurred."
                : "Your request could not be processed.";
        }

        String machineCode = resolved != null
            ? HttpErrorMapping.machineCode(resolved)
            : (statusCode >= 500 ? "INTERNAL" : "UNKNOWN");

        if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
            return ResponseEntity.status(statusCode)
                .contentType(ProtobufMediaTypes.APPLICATION_X_PROTOBUF)
                .body(ApiError.newBuilder()
                    .setStatusCode(statusCode)
                    .setCode(machineCode)
                    .setMessage(errorMessage)
                    .build());
        }

        ErrorResponse errorResponse = new ErrorResponse(
            statusCode,
            error,
            errorMessage);

        return ResponseEntity.status(statusCode).body(errorResponse);
    }

    @Setter
    @Getter
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;

        public ErrorResponse(int status, String error, String message) {
            this.status = status;
            this.error = error;
            this.message = message;
        }
    }
}
