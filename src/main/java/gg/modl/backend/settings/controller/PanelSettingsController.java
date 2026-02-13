package gg.modl.backend.settings.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.*;
import gg.modl.backend.settings.service.*;
import gg.modl.backend.storage.service.S3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiredArgsConstructor
public class PanelSettingsController {
    private final PunishmentTypeService punishmentTypeService;
    private final GeneralSettingsService generalSettingsService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final WebhookSettingsService webhookSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final DomainSettingsService domainSettingsService;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final S3StorageService s3StorageService;
    private final AITicketAnalysisService aiTicketAnalysisService;
    private final OffenderThresholdSettingsService offenderThresholdSettingsService;
    private final PermissionService permissionService;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_ICON_SIZE = 2 * 1024 * 1024; // 2MB

    @GetMapping("/punishment-types")
    public ResponseEntity<List<PunishmentType>> getPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        return ResponseEntity.ok(types);
    }

    @GetMapping("/punishment-types/{ordinal}")
    public ResponseEntity<PunishmentType> getPunishmentType(
            @PathVariable int ordinal,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/punishment-types/{ordinal}")
    public ResponseEntity<PunishmentType> updatePunishmentType(
            @PathVariable int ordinal,
            @RequestBody @Valid PunishmentType updatedType,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            PunishmentType result = punishmentTypeService.updatePunishmentType(server, ordinal, updatedType);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/punishment-types")
    public ResponseEntity<PunishmentType> createPunishmentType(
            @RequestBody @Valid PunishmentType newType,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        PunishmentType created = punishmentTypeService.createPunishmentType(server, newType);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/punishment-types/reset")
    public ResponseEntity<List<PunishmentType>> resetPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.initializeDefaultTypes(server);
        return ResponseEntity.ok(types);
    }

    @DeleteMapping("/punishment-types/{ordinal}")
    public ResponseEntity<?> deletePunishmentType(
            @PathVariable int ordinal,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (ordinal < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete core administrative punishment types"));
        }

        try {
            boolean deleted = punishmentTypeService.deletePunishmentType(server, ordinal);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Punishment type deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/general")
    public ResponseEntity<?> getGeneralSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        GeneralSettings settings = generalSettingsService.getGeneralSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/general")
    public ResponseEntity<?> updateGeneralSettings(
            @RequestBody GeneralSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        GeneralSettings updated = generalSettingsService.updateGeneralSettings(server, settings);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/status-thresholds")
    public ResponseEntity<OffenderThresholdSettings> getStatusThresholds(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        OffenderThresholdSettings settings = offenderThresholdSettingsService.getThresholdSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/status-thresholds")
    public ResponseEntity<OffenderThresholdSettings> updateStatusThresholds(
            @RequestBody OffenderThresholdSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        OffenderThresholdSettings updated = offenderThresholdSettingsService.updateThresholdSettings(server, settings);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/api-keys/{type}/generate")
    public ResponseEntity<?> generateApiKey(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        String apiKey = apiKeySettingsService.generateApiKey(server, type);
        return ResponseEntity.ok(Map.of(
                "message", "API key generated successfully",
                "apiKey", apiKey
        ));
    }

    @GetMapping("/api-keys/{type}/reveal")
    public ResponseEntity<?> revealApiKey(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        String apiKey = apiKeySettingsService.revealApiKey(server, type);

        if (apiKey == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("apiKey", apiKey));
    }

    @DeleteMapping("/api-keys/{type}")
    public ResponseEntity<?> deleteApiKey(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        boolean deleted = apiKeySettingsService.deleteApiKey(server, type);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("message", "API key deleted successfully"));
    }

    @GetMapping("/api-keys/{type}/exists")
    public ResponseEntity<?> checkApiKeyExists(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;
        boolean exists = apiKeySettingsService.hasApiKey(server, type);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping("/ai-moderation")
    public ResponseEntity<AIModerationSettings> getAIModerationSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/ai-moderation")
    public ResponseEntity<AIModerationSettings> updateAIModerationSettings(
            @RequestBody AIModerationSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        AIModerationSettings updated = aiModerationSettingsService.updateAIModerationSettings(server, settings);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/webhooks")
    public ResponseEntity<WebhookSettings> getWebhookSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        WebhookSettings settings = webhookSettingsService.getWebhookSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/webhooks")
    public ResponseEntity<WebhookSettings> updateWebhookSettings(
            @RequestBody WebhookSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        WebhookSettings updated = webhookSettingsService.updateWebhookSettings(server, settings);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/webhooks/test")
    public ResponseEntity<?> testWebhook(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        boolean success = webhookSettingsService.testWebhook(server);

        if (success) {
            return ResponseEntity.ok(Map.of("message", "Webhook test sent successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to send webhook test"));
        }
    }

    @GetMapping("/ticket-forms")
    public ResponseEntity<TicketFormSettings> getTicketFormSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        TicketFormSettings settings = ticketFormSettingsService.getTicketFormSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/ticket-forms")
    public ResponseEntity<TicketFormSettings> updateTicketFormSettings(
            @RequestBody TicketFormSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketFormSettings updated = ticketFormSettingsService.updateTicketFormSettings(server, settings);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/ticket-forms/{type}")
    public ResponseEntity<TicketFormSettings.TicketForm> getTicketForm(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketFormSettings.TicketForm form = ticketFormSettingsService.getFormByType(server, type);

        if (form == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(form);
    }

    @PutMapping("/ticket-forms/{type}")
    public ResponseEntity<TicketFormSettings> updateTicketForm(
            @PathVariable String type,
            @RequestBody TicketFormSettings.TicketForm form,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketFormSettings updated = ticketFormSettingsService.updateFormByType(server, type, form);
        return ResponseEntity.ok(updated);
    }

    // Domain Settings Endpoints
    @GetMapping("/domain")
    public ResponseEntity<DomainSettings> getDomainSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String host = request.getHeader("Host");
        DomainSettings settings = domainSettingsService.getDomainSettings(server, host);
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/domain")
    public ResponseEntity<?> configureDomain(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String customDomain = body.get("customDomain");
        
        if (customDomain == null || customDomain.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Custom domain is required"));
        }
        
        try {
            DomainSettings settings = domainSettingsService.configureDomain(server, customDomain.trim());
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/domain/verify")
    public ResponseEntity<?> verifyDomain(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String domain = body.get("domain");

        if (domain == null || domain.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Domain is required"));
        }

        try {
            DomainSettings settings = domainSettingsService.verifyDomain(server, domain.trim());
            DomainSettings.DomainStatus status = settings.getStatus();

            String message = switch (status.getStatus()) {
                case "active" -> status.getSslStatus().equals("active")
                        ? "Domain verified successfully with active SSL!"
                        : "Domain verified! SSL certificate is being provisioned.";
                case "error" -> status.getError() != null
                        ? status.getError()
                        : "Domain verification failed";
                default -> "Domain verification pending. Please ensure your CNAME is configured correctly.";
            };

            return ResponseEntity.ok(Map.of(
                    "status", status,
                    "message", message
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/domain")
    public ResponseEntity<?> removeDomain(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            domainSettingsService.removeDomain(server);
            return ResponseEntity.ok(Map.of("message", "Domain removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/quick-responses")
    public ResponseEntity<?> getQuickResponses(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        var settings = quickResponseSettingsService.getQuickResponseSettings(server);

        if (settings == null) {
            return ResponseEntity.ok(Map.of("categories", List.of()));
        }

        return ResponseEntity.ok(settings);
    }

    @PutMapping("/quick-responses")
    public ResponseEntity<?> updateQuickResponses(
            @RequestBody Map<String, Object> quickResponses,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        quickResponseSettingsService.updateQuickResponseSettings(server, quickResponses);
        return ResponseEntity.ok(Map.of("message", "Quick responses updated successfully"));
    }

    @PostMapping("/upload-icon")
    public ResponseEntity<?> uploadIcon(
            @RequestParam("icon") MultipartFile file,
            @RequestParam("iconType") String iconType,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireSuperAdmin(server, request);
        if (denied != null) return denied;

        // Validate icon type
        if (!iconType.equals("homepage") && !iconType.equals("panel")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid icon type. Must be 'homepage' or 'panel'."));
        }

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Allowed: PNG, JPEG, GIF, WebP, SVG"));
        }

        // Validate file size
        if (file.getSize() > MAX_ICON_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 2MB."));
        }

        // Check if S3 is configured
        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "File storage is not configured"));
        }

        try {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "icon";
            String url = s3StorageService.uploadFile(
                    server,
                    "icons/" + iconType,
                    fileName,
                    contentType,
                    file.getBytes()
            );

            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read file"));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> requireSuperAdmin(Server server, HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (!permissionService.isSuperAdmin(server, email)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the super admin can access server configuration"));
        }
        return null;
    }

    @PostMapping("/ai-apply-punishment/{ticketId}")
    public ResponseEntity<?> applyAIPunishment(
            @PathVariable String ticketId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffName = body.getOrDefault("staffName", "Staff");

        var result = aiTicketAnalysisService.applyAISuggestion(server, ticketId, staffName);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("error", result.error()));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/ai-dismiss-suggestion/{ticketId}")
    public ResponseEntity<?> dismissAISuggestion(
            @PathVariable String ticketId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        var result = aiTicketAnalysisService.dismissAISuggestion(server, ticketId);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("error", result.error()));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
