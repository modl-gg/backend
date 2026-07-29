package gg.modl.backend.storage.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.storage.dto.request.EvidenceConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.EvidencePresignUploadRequest;
import gg.modl.backend.storage.dto.request.SubmitEvidenceRequest;
import gg.modl.backend.storage.service.EvidenceUploadService;
import gg.modl.backend.storage.service.EvidenceUploadService.ConfirmUploadResult;
import gg.modl.backend.storage.service.EvidenceUploadService.PresignUploadResult;
import gg.modl.backend.storage.service.EvidenceUploadService.SubmitEvidenceResult;
import gg.modl.backend.storage.service.UploadOrchestrationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

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
            return error(HttpStatus.NOT_FOUND, "Invalid or expired upload token");
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
        PresignUploadResult result = evidenceUploadService.presignUpload(token, body);
        return switch (result) {
            case PresignUploadResult.InvalidToken ignored -> error(HttpStatus.NOT_FOUND, "Invalid or expired upload token");
            case PresignUploadResult.StorageNotConfigured ignored -> error(HttpStatus.SERVICE_UNAVAILABLE, "File storage is not configured");
            case PresignUploadResult.ServerNotFound ignored -> error(HttpStatus.NOT_FOUND, "Server not found");
            case PresignUploadResult.Orchestrated orchestrated -> presignResponse(orchestrated.outcome());
        };
    }

    private ResponseEntity<Map<String, Object>> presignResponse(UploadOrchestrationService.PresignOutcome outcome) {
        return switch (outcome.status()) {
            case QUOTA_EXCEEDED ->
                error(HttpStatus.BAD_REQUEST, "Storage quota exceeded. Please contact the server administrator.");
            case VALIDATION_FAILED, TEMP_LIMIT_EXCEEDED -> error(HttpStatus.BAD_REQUEST, outcome.message());
            case SUCCESS -> ResponseEntity.ok(Map.of(
                "status", 200,
                "presignedUrl", outcome.upload().presignedUrl(),
                "key", outcome.upload().key(),
                "expiresAt", outcome.upload().expiresAt().toString(),
                "method", outcome.upload().method(),
                "requiredHeaders", outcome.upload().requiredHeaders()
            ));
        };
    }

    @PostMapping("/{token}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(
        @PathVariable String token,
        @RequestBody @Valid EvidenceConfirmUploadRequest body
    ) {
        ConfirmUploadResult result = evidenceUploadService.confirmUpload(token, body);
        return switch (result) {
            case ConfirmUploadResult.InvalidToken ignored -> error(HttpStatus.NOT_FOUND, "Invalid or expired upload token");
            case ConfirmUploadResult.InvalidKey ignored ->
                error(HttpStatus.FORBIDDEN, "Upload key does not belong to this evidence token");
            case ConfirmUploadResult.Orchestrated orchestrated -> confirmResponse(orchestrated.outcome());
        };
    }

    private ResponseEntity<Map<String, Object>> confirmResponse(UploadOrchestrationService.ConfirmOutcome outcome) {
        return switch (outcome.status()) {
            case UPLOAD_NOT_FOUND -> error(HttpStatus.NOT_FOUND, "Upload not found. File may not have been uploaded yet.");
            case QUOTA_EXCEEDED -> error(HttpStatus.BAD_REQUEST, "Storage quota exceeded");
            case RECORD_FAILED, TEMP_LIMIT_EXCEEDED -> error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to record upload");
            case SUCCESS -> ResponseEntity.ok(Map.of(
                "status", 200,
                "key", outcome.upload().key(),
                "url", outcome.upload().url(),
                "fileName", outcome.upload().fileName(),
                "size", outcome.upload().size(),
                "contentType", outcome.upload().contentType()
            ));
        };
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<Map<String, Object>> submitEvidence(
        @PathVariable String token,
        @RequestBody @Valid SubmitEvidenceRequest request
    ) {
        SubmitEvidenceResult result = evidenceUploadService.submitEvidence(token, request);
        return switch (result) {
            case SubmitEvidenceResult.InvalidToken ignored -> error(HttpStatus.NOT_FOUND, "Invalid or expired upload token");
            case SubmitEvidenceResult.ServerNotFound ignored -> error(HttpStatus.NOT_FOUND, "Server not found");
            case SubmitEvidenceResult.InvalidUrl ignored -> error(HttpStatus.BAD_REQUEST, "Invalid evidence URL");
            case SubmitEvidenceResult.PunishmentNotFound notFound ->
                error(HttpStatus.NOT_FOUND, notFound.message() != null ? notFound.message() : "Punishment not found");
            case SubmitEvidenceResult.Success ignored -> ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Evidence uploaded successfully"
            ));
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
            .map(err -> err instanceof FieldError fe ? fe.getField() + ": " + fe.getDefaultMessage() : err.getDefaultMessage())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("Invalid request body");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleHandlerValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
