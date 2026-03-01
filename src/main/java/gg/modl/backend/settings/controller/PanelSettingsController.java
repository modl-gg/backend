package gg.modl.backend.settings.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.data.TicketLabelSettings;
import gg.modl.backend.settings.data.WebhookSettings;
import gg.modl.backend.settings.dto.request.ApplyAIPunishmentRequest;
import gg.modl.backend.settings.dto.request.ConfigureDomainRequest;
import gg.modl.backend.settings.dto.request.PatchGeneralSettingsRequest;
import gg.modl.backend.settings.dto.request.PatchQuickResponsesRequest;
import gg.modl.backend.settings.dto.request.PatchStatusThresholdSettingsRequest;
import gg.modl.backend.settings.dto.request.PatchTicketFormSettingsRequest;
import gg.modl.backend.settings.dto.request.PatchTicketLabelSettingsRequest;
import gg.modl.backend.settings.dto.request.UpdateQuickResponsesRequest;
import gg.modl.backend.settings.dto.request.VerifyDomainRequest;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import gg.modl.backend.settings.service.CustomDomainAccessService;
import gg.modl.backend.settings.service.DomainSettingsService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.settings.service.QuickResponseSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import gg.modl.backend.settings.service.TicketLabelSettingsService;
import gg.modl.backend.settings.service.VersionedSettings;
import gg.modl.backend.settings.service.WebhookSettingsService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiredArgsConstructor
public class PanelSettingsController {
    private final PunishmentTypeService punishmentTypeService;
    private final GeneralSettingsService generalSettingsService;
    private final TicketLabelSettingsService ticketLabelSettingsService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final WebhookSettingsService webhookSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final DomainSettingsService domainSettingsService;
    private final CustomDomainAccessService customDomainAccessService;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final S3StorageService s3StorageService;
    private final StorageQuotaService storageQuotaService;
    private final AITicketAnalysisService aiTicketAnalysisService;
    private final OffenderThresholdSettingsService offenderThresholdSettingsService;

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

    @PatchMapping("/punishment-types/{ordinal}")
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
    public ResponseEntity<SettingsEnvelope<GeneralSettings>> getGeneralSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(toEnvelope(generalSettingsService.getGeneralSettingsState(server)));
    }

    @PatchMapping("/general")
    public ResponseEntity<SettingsEnvelope<GeneralSettings>> patchGeneralSettings(
            @RequestBody @Valid PatchGeneralSettingsRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        GeneralSettings patch = GeneralSettings.builder()
                .serverDisplayName(body.serverDisplayName())
                .discordWebhookUrl(body.discordWebhookUrl())
                .homepageIconUrl(body.homepageIconUrl())
                .panelIconUrl(body.panelIconUrl())
                .build();

        VersionedSettings<GeneralSettings> updated = generalSettingsService.patchGeneralSettings(
                server,
                body.expectedVersion(),
                patch
        );
        return ResponseEntity.ok(toEnvelope(updated));
    }

    @GetMapping("/ticket-labels")
    public ResponseEntity<SettingsEnvelope<TicketLabelSettings>> getTicketLabelSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(toEnvelope(ticketLabelSettingsService.getTicketLabelSettingsState(server)));
    }

    @PatchMapping("/ticket-labels")
    public ResponseEntity<SettingsEnvelope<TicketLabelSettings>> patchTicketLabelSettings(
            @RequestBody @Valid PatchTicketLabelSettingsRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<TicketLabelSettings> updated = ticketLabelSettingsService.patchTicketLabelSettings(
                server,
                body.expectedVersion(),
                body.labels()
        );
        return ResponseEntity.ok(toEnvelope(updated));
    }

    @GetMapping("/status-thresholds")
    public ResponseEntity<SettingsEnvelope<OffenderThresholdSettings>> getStatusThresholds(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(toEnvelope(offenderThresholdSettingsService.getThresholdSettingsState(server)));
    }

    @PatchMapping("/status-thresholds")
    public ResponseEntity<SettingsEnvelope<OffenderThresholdSettings>> patchStatusThresholds(
            @RequestBody @Valid PatchStatusThresholdSettingsRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<OffenderThresholdSettings> updated = offenderThresholdSettingsService.patchThresholdSettings(
                server,
                body.expectedVersion(),
                body.settings()
        );
        return ResponseEntity.ok(toEnvelope(updated));
    }

    @PostMapping("/api-keys/{type}/generate")
    public ResponseEntity<?> generateApiKey(
            @PathVariable String type,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
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
        boolean exists = apiKeySettingsService.hasApiKey(server, type);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping("/ai-moderation")
    public ResponseEntity<AIModerationSettings> getAIModerationSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PatchMapping("/ai-moderation")
    public ResponseEntity<AIModerationSettings> updateAIModerationSettings(
            @RequestBody @Valid AIModerationSettings settings,
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

    @PatchMapping("/webhooks")
    public ResponseEntity<WebhookSettings> updateWebhookSettings(
            @RequestBody @Valid WebhookSettings settings,
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
    public ResponseEntity<SettingsEnvelope<TicketFormSettings>> getTicketFormSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(toEnvelope(ticketFormSettingsService.getTicketFormSettingsState(server)));
    }

    @PatchMapping("/ticket-forms")
    public ResponseEntity<SettingsEnvelope<TicketFormSettings>> patchTicketFormSettings(
            @RequestBody @Valid PatchTicketFormSettingsRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<TicketFormSettings> updated = ticketFormSettingsService.patchTicketFormSettings(
                server,
                body.expectedVersion(),
                body.settings()
        );
        return ResponseEntity.ok(toEnvelope(updated));
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

    @GetMapping("/domain")
    public ResponseEntity<DomainSettings> getDomainSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String host = request.getHeader("Host");
        DomainSettings settings = domainSettingsService.getDomainSettings(server, host);
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/domain")
    public ResponseEntity<?> configureDomain(
            @RequestBody @Valid ConfigureDomainRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            DomainSettings settings = domainSettingsService.configureDomain(server, body.customDomain().trim());
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/domain/verify")
    public ResponseEntity<?> verifyDomain(
            @RequestBody @Valid VerifyDomainRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            DomainSettings settings = domainSettingsService.verifyDomain(server, body.domain().trim());
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
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            domainSettingsService.removeDomain(server);
            return ResponseEntity.ok(Map.of("message", "Domain removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/quick-responses")
    public ResponseEntity<SettingsEnvelope<QuickResponseSettings>> getQuickResponses(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(toEnvelope(quickResponseSettingsService.getQuickResponseSettingsState(server)));
    }

    @PatchMapping("/quick-responses")
    public ResponseEntity<SettingsEnvelope<QuickResponseSettings>> patchQuickResponses(
            @RequestBody @Valid PatchQuickResponsesRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<QuickResponseSettings> updated = quickResponseSettingsService.patchQuickResponseSettings(
                server,
                body.expectedVersion(),
                new UpdateQuickResponsesRequest(body.categories())
        );
        return ResponseEntity.ok(toEnvelope(updated));
    }

    @PostMapping("/upload-icon")
    public ResponseEntity<?> uploadIcon(
            @RequestParam("icon") MultipartFile file,
            @RequestParam("iconType") String iconType,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (!iconType.equals("homepage") && !iconType.equals("panel")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid icon type. Must be 'homepage' or 'panel'."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type. Allowed: PNG, JPEG, GIF, WebP, SVG"));
        }

        if (file.getSize() > MAX_ICON_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large. Maximum size is 2MB."));
        }

        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "File storage is not configured"));
        }

        if (!storageQuotaService.canUpload(server, file.getSize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Storage quota exceeded"));
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

    @PostMapping("/ai-apply-punishment/{ticketId}")
    public ResponseEntity<?> applyAIPunishment(
            @PathVariable String ticketId,
            @RequestBody @Valid ApplyAIPunishmentRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String staffName = body.staffName() != null ? body.staffName() : "Staff";

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

    private <T> SettingsEnvelope<T> toEnvelope(VersionedSettings<T> settings) {
        return new SettingsEnvelope<>(
                settings.data(),
                new SettingsMeta(settings.version(), settings.updatedAt())
        );
    }

    public record SettingsEnvelope<T>(T data, SettingsMeta _meta) {
    }

    public record SettingsMeta(long version, Date updatedAt) {
    }

    private ResponseEntity<?> requireCustomDomainWriteAccess(Server server) {
        if (customDomainAccessService.canManageCustomDomain(server)) {
            return null;
        }

        return ResponseEntity.status(403).body(Map.of(
                "message", "Custom domains require Premium unless your server is grandfathered."
        ));
    }
}
