package gg.modl.backend.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller that handles requests to the deprecated /api endpoint.
 * Returns 410 Gone to signal that clients should use the V2 API (api.modl.gg).
 */
@RestController
@RequestMapping("/api")
public class DeprecatedApiController {

    /**
     * Handles all requests to /api and /api/** paths.
     * Returns 410 Gone to indicate this API version is deprecated.
     */
    @RequestMapping({"", "/**"})
    public ResponseEntity<Map<String, Object>> handleDeprecatedApi() {
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(Map.of(
                        "status", 410,
                        "error", "Gone",
                        "message", "This API version has been deprecated. Please use the V2 API at api.modl.gg",
                        "v2ApiUrl", "https://api.modl.gg/v1"
                ));
    }
}