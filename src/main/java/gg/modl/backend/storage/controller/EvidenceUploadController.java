package gg.modl.backend.storage.controller;

import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.storage.dto.request.EvidenceConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.EvidencePresignUploadRequest;
import gg.modl.backend.storage.dto.request.SubmitEvidenceRequest;
import gg.modl.backend.storage.service.EvidenceUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_EVIDENCE_UPLOAD)
@RequiredArgsConstructor
@Slf4j
public class EvidenceUploadController {

    private final EvidenceUploadService evidenceUploadService;

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> validateToken(@PathVariable String token) {
        EvidenceUploadService.TokenValidationResult result = evidenceUploadService.validateToken(token);
        if (!result.valid()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "punishmentId", result.info().punishmentId(),
                "playerName", result.info().playerName(),
                "issuerName", result.info().issuerName()
        ));
    }

    @PostMapping("/{token}/presign")
    public ResponseEntity<Map<String, Object>> presignUpload(
            @PathVariable String token,
            @RequestBody @Valid EvidencePresignUploadRequest body
    ) {
        EvidenceUploadService.PresignUploadResult result = evidenceUploadService.presignUpload(token, body);
        return switch (result.status()) {
            case INVALID_TOKEN -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
            case STORAGE_NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", 503,
                    "message", "File storage is not configured"
            ));
            case SERVER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Server not found"
            ));
            case VALIDATION_FAILED, QUOTA_EXCEEDED -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", result.message()
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "presignedUrl", result.upload().presignedUrl(),
                    "key", result.upload().key(),
                    "expiresAt", result.upload().expiresAt().toString(),
                    "method", result.upload().method(),
                    "requiredHeaders", result.upload().requiredHeaders()
            ));
        };
    }

    @PostMapping("/{token}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(
            @PathVariable String token,
            @RequestBody @Valid EvidenceConfirmUploadRequest body
    ) {
        EvidenceUploadService.ConfirmUploadResult result = evidenceUploadService.confirmUpload(token, body);
        return switch (result.status()) {
            case INVALID_TOKEN -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
            case INVALID_KEY -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", 403,
                    "message", "Upload key does not belong to this evidence token"
            ));
            case UPLOAD_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Upload not found. File may not have been uploaded yet."
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "key", result.upload().key(),
                    "url", result.upload().url(),
                    "fileName", result.upload().fileName(),
                    "size", result.upload().size(),
                    "contentType", result.upload().contentType()
            ));
        };
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<Map<String, Object>> submitEvidence(
            @PathVariable String token,
            @RequestBody @Valid SubmitEvidenceRequest request
    ) {
        EvidenceUploadService.SubmitEvidenceResult result = evidenceUploadService.submitEvidence(token, request);
        return switch (result.status()) {
            case INVALID_TOKEN -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
            case SERVER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Server not found"
            ));
            case INVALID_URL -> ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Invalid evidence URL"
            ));
            case PUNISHMENT_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", result.message() != null ? result.message() : "Punishment not found"
            ));
            case SUCCESS -> ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", "Evidence uploaded successfully"
            ));
        };
    }
}