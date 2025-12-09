package gg.modl.backend.rest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 500;
        
        String errorMessage;
        String error;
        
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            error = "Not Found";
            errorMessage = "The requested resource was not found.";
        } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            error = "Internal Server Error";
            errorMessage = "An internal server error occurred.";
        } else {
            error = "Error";
            errorMessage = "Your request is missing required data.";
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                statusCode,
                error,
                errorMessage);
        
        return new ResponseEntity<>(errorResponse, HttpStatus.valueOf(statusCode));
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