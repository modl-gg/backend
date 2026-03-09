package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.request.ConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.PresignUploadRequest;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketBucket;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_MEDIA)
@RequiredArgsConstructor
public class PublicMediaController {
    private final S3StorageService s3StorageService;
    private final MediaValidationService validationService;
    private final StorageQuotaService quotaService;
    private final TicketService ticketService;
    private final TicketEmailVerificationService verificationService;

    private static final Set<String> PUBLIC_ALLOWED_UPLOAD_TYPES = Set.of("ticket", "tickets", "appeal");

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMediaConfig(HttpServletRequest request) {
        boolean isConfigured = s3StorageService.isConfigured();
        String cdnDomain = s3StorageService.getCdnDomain();
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;

        Map<String, Object> supportedTypes = isConfigured
                ? validationService.getAllSupportedTypes()
                : Map.of("evidence", List.of(), "tickets", List.of(), "appeals", List.of(), "articles", List.of(), "server-icons", List.of());

        Map<String, Object> fileSizeLimits = isConfigured
                ? validationService.getAllSizeLimits(isPremium)
                : Map.of("evidence", 0L, "tickets", 0L, "appeals", 0L, "articles", 0L, "server-icons", 0L);

        Map<String, Object> response = new HashMap<>();
        response.put("backblazeConfigured", isConfigured);
        response.put("supportedTypes", supportedTypes);
        response.put("fileSizeLimits", fileSizeLimits);
        response.put("cdnDomain", cdnDomain != null && !cdnDomain.isBlank() ? cdnDomain : null);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/presign")
    public ResponseEntity<?> getPresignedUploadUrl(
            @RequestBody @Valid PresignUploadRequest presignRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean isPremium = server.getPlan() == ServerPlan.PREMIUM;
        String normalizedEntityId = presignRequest.entityId() != null ? presignRequest.entityId().trim() : null;

        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(presignRequest.uploadType())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Upload type not allowed for public uploads. Allowed: " + PUBLIC_ALLOWED_UPLOAD_TYPES
            ));
        }

        ResponseEntity<?> accessCheck = validatePublicUploadAccess(
                server,
                presignRequest.uploadType(),
                normalizedEntityId,
                presignRequest.accessToken()
        );
        if (accessCheck != null) {
            return accessCheck;
        }

        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
                presignRequest.fileName(),
                presignRequest.contentType(),
                presignRequest.fileSize(),
                normalizeUploadType(presignRequest.uploadType()),
                isPremium
        );

        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.error()));
        }

        if (!quotaService.canUpload(server, presignRequest.fileSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
        }

        try {
            PresignUploadResponse response = s3StorageService.createPresignedUploadUrl(
                    server,
                    normalizeUploadType(presignRequest.uploadType()),
                    presignRequest.fileName(),
                    presignRequest.contentType(),
                    presignRequest.fileSize(),
                    normalizedEntityId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmUpload(
            @RequestBody @Valid ConfirmUploadRequest confirmRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String key = confirmRequest.key();

        if (!key.startsWith(server.getDatabaseName() + "/")) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        String uploadType = extractUploadType(key);
        String entityId = extractEntityId(key);
        if (!PUBLIC_ALLOWED_UPLOAD_TYPES.contains(uploadType)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Upload type not allowed for public confirmation"
            ));
        }
        if (entityId == null || entityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid upload key"));
        }

        ResponseEntity<?> accessCheck = validatePublicUploadAccess(
                server,
                uploadType,
                entityId,
                confirmRequest.accessToken()
        );
        if (accessCheck != null) {
            return accessCheck;
        }

        UploadResponse uploadDetails = s3StorageService.getUploadDetails(key);
        if (uploadDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Upload not found",
                    "message", "The file was not uploaded or the presigned URL expired"
            ));
        }

        return ResponseEntity.ok(uploadDetails);
    }

    private String extractUploadType(String key) {
        String[] parts = key.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }

    private String extractEntityId(String key) {
        String[] parts = key.split("/");
        return parts.length >= 4 ? parts[2] : null;
    }

    private String normalizeUploadType(String uploadType) {
        return "tickets".equals(uploadType) ? "ticket" : uploadType;
    }

    private ResponseEntity<?> validatePublicUploadAccess(
            Server server,
            String uploadType,
            String entityId,
            String accessToken
    ) {
        if (entityId == null || entityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "entityId is required for public uploads"));
        }

        String normalizedEntityId = entityId.trim();
        String normalizedType = normalizeUploadType(uploadType);

        // Public creation flows upload before a ticket/appeal id exists.
        // "new" keeps this backward-compatible while still requiring explicit intent.
        if ("new".equalsIgnoreCase(normalizedEntityId)) {
            if ("ticket".equals(normalizedType) || "appeal".equals(normalizedType)) {
                return null;
            }
            return ResponseEntity.status(403).body(Map.of("error", "Temporary uploads are only allowed for ticket and appeal types"));
        }

        Optional<Ticket> ticketOpt = ticketService.getTicketRaw(server, normalizedEntityId);
        if (ticketOpt.isEmpty() || ticketOpt.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = ticketOpt.get();
        boolean isAppealTicket = ticket.getType() == TicketBucket.APPEAL;
        if ("appeal".equals(normalizedType) && !isAppealTicket) {
            return ResponseEntity.status(403).body(Map.of("error", "Entity is not an appeal ticket"));
        }
        if ("ticket".equals(normalizedType) && isAppealTicket) {
            return ResponseEntity.status(403).body(Map.of("error", "Appeal uploads must use uploadType=appeal"));
        }

        if (ticket.isEmailAuthEnabled()) {
            boolean validToken = accessToken != null
                    && !accessToken.isBlank()
                    && verificationService.validateToken(server, normalizedEntityId, accessToken);
            if (!validToken) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Email verification token required for this ticket"
                ));
            }
        }

        return null;
    }
}
