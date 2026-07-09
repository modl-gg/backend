package gg.modl.backend.settings.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.data.TicketLabelSettings;
import gg.modl.backend.settings.data.WebhookSettings;
import gg.modl.backend.settings.dto.request.PatchReplayRetentionSettingsRequest;
import gg.modl.backend.settings.dto.request.UpdateQuickResponsesRequest;
import gg.modl.backend.settings.service.AIModerationSettingsService;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.IconUploadService;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.QuickResponseSettingsService;
import gg.modl.backend.settings.service.ReplayRetentionSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import gg.modl.backend.settings.service.TicketLabelSettingsService;
import gg.modl.backend.settings.service.VersionedSettings;
import gg.modl.backend.settings.service.WebhookSettingsService;
import gg.modl.proto.modl.v1.AISuggestionActionResponse;
import gg.modl.proto.modl.v1.ApiKeyDeleteResponse;
import gg.modl.proto.modl.v1.ApiKeyExistsResponse;
import gg.modl.proto.modl.v1.ApiKeyGenerateResponse;
import gg.modl.proto.modl.v1.ApiKeyRevealResponse;
import gg.modl.proto.modl.v1.GeneralSettingsEnvelope;
import gg.modl.proto.modl.v1.OffenderThresholdSettingsEnvelope;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PatchGeneralSettingsRequest;
import gg.modl.proto.modl.v1.PatchQuickResponsesRequest;
import gg.modl.proto.modl.v1.PatchStatusThresholdSettingsRequest;
import gg.modl.proto.modl.v1.PatchTicketFormSettingsRequest;
import gg.modl.proto.modl.v1.PatchTicketLabelSettingsRequest;
import gg.modl.proto.modl.v1.QuickResponseSettingsEnvelope;
import gg.modl.proto.modl.v1.ReplayRetentionSettingsEnvelope;
import gg.modl.proto.modl.v1.TicketForm;
import gg.modl.proto.modl.v1.TicketFormSettingsEnvelope;
import gg.modl.proto.modl.v1.TicketLabelSettingsEnvelope;
import gg.modl.proto.modl.v1.UpdateAIModerationSettingsRequest;
import gg.modl.proto.modl.v1.UpdateWebhookSettingsRequest;
import gg.modl.proto.modl.v1.WebhookTestResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
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

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiredArgsConstructor
public class PanelSettingsController {
    private final GeneralSettingsService generalSettingsService;
    private final TicketLabelSettingsService ticketLabelSettingsService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final WebhookSettingsService webhookSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final QuickResponseSettingsService quickResponseSettingsService;
    private final IconUploadService iconUploadService;
    private final AITicketAnalysisService aiTicketAnalysisService;
    private final OffenderThresholdSettingsService offenderThresholdSettingsService;
    private final PermissionService permissionService;
    private final ReplayRetentionSettingsService replayRetentionSettingsService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final Validator validator;

    @GetMapping("/general")
    public GeneralSettingsEnvelope getGeneralSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toGeneralSettingsEnvelope(generalSettingsService.getGeneralSettingsState(server));
    }

    @PatchMapping("/general")
    public GeneralSettingsEnvelope patchGeneralSettings(
        @RequestBody PatchGeneralSettingsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<GeneralSettings> updated = generalSettingsService.patchGeneralSettings(
            server,
            body.getExpectedVersion(),
            PanelSettingsProtoMapper.fromPatchGeneralSettingsRequest(body)
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toGeneralSettingsEnvelope(updated);
    }

    @GetMapping("/ticket-labels")
    public TicketLabelSettingsEnvelope getTicketLabelSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toTicketLabelSettingsEnvelope(
            ticketLabelSettingsService.getTicketLabelSettingsState(server));
    }

    @PatchMapping("/ticket-labels")
    public TicketLabelSettingsEnvelope patchTicketLabelSettings(
        @RequestBody PatchTicketLabelSettingsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<TicketLabelSettings> updated = ticketLabelSettingsService.patchTicketLabelSettings(
            server,
            body.getExpectedVersion(),
            PanelSettingsProtoMapper.fromPatchTicketLabelSettingsRequest(body)
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toTicketLabelSettingsEnvelope(updated);
    }

    @GetMapping("/status-thresholds")
    public OffenderThresholdSettingsEnvelope getStatusThresholds(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toOffenderThresholdSettingsEnvelope(
            offenderThresholdSettingsService.getThresholdSettingsState(server));
    }

    @PatchMapping("/status-thresholds")
    public OffenderThresholdSettingsEnvelope patchStatusThresholds(
        @RequestBody PatchStatusThresholdSettingsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<OffenderThresholdSettings> updated = offenderThresholdSettingsService.patchThresholdSettings(
            server,
            body.getExpectedVersion(),
            PanelSettingsProtoMapper.fromOffenderThresholdSettings(body.getSettings())
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toOffenderThresholdSettingsEnvelope(updated);
    }

    @GetMapping("/replay-retention")
    public ReplayRetentionSettingsEnvelope getReplayRetentionSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toReplayRetentionSettingsEnvelope(
            replayRetentionSettingsService.getReplayRetentionSettingsState(server));
    }

    @PatchMapping("/replay-retention")
    public ReplayRetentionSettingsEnvelope patchReplayRetentionSettings(
        @RequestBody @Valid PatchReplayRetentionSettingsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<ReplayRetentionSettings> updated = replayRetentionSettingsService.patchReplayRetentionSettings(
            server,
            body.expectedVersion(),
            body.enabled(),
            body.days()
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toReplayRetentionSettingsEnvelope(updated);
    }

    @PostMapping("/api-keys/{type}/generate")
    public ApiKeyGenerateResponse generateApiKey(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        String apiKey = apiKeySettingsService.generateApiKey(server, type);
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toApiKeyGenerateResponse("API key generated successfully", apiKey);
    }

    @GetMapping("/api-keys/{type}/reveal")
    public ResponseEntity<ApiKeyRevealResponse> revealApiKey(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        String apiKey = apiKeySettingsService.revealApiKey(server, type);

        if (apiKey == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PanelSettingsProtoMapper.toApiKeyRevealResponse(apiKey));
    }

    @DeleteMapping("/api-keys/{type}")
    public ResponseEntity<ApiKeyDeleteResponse> deleteApiKey(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        boolean deleted = apiKeySettingsService.deleteApiKey(server, type);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        invalidateSettings(server);
        return ResponseEntity.ok(PanelSettingsProtoMapper.toApiKeyDeleteResponse("API key deleted successfully"));
    }

    @GetMapping("/api-keys/{type}/exists")
    public ApiKeyExistsResponse checkApiKeyExists(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireSuperAdmin(server, request);
        boolean exists = apiKeySettingsService.hasApiKey(server, type);
        return PanelSettingsProtoMapper.toApiKeyExistsResponse(exists);
    }

    @GetMapping("/ai-moderation")
    public gg.modl.proto.modl.v1.AIModerationSettings getAIModerationSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        AIModerationSettings settings = aiModerationSettingsService.getAIModerationSettings(server);
        return PanelSettingsProtoMapper.toAIModerationSettings(settings);
    }

    @PatchMapping("/ai-moderation")
    public gg.modl.proto.modl.v1.AIModerationSettings updateAIModerationSettings(
        @RequestBody UpdateAIModerationSettingsRequest requestBody,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        AIModerationSettings settings = PanelSettingsProtoMapper.fromUpdateAIModerationSettingsRequest(requestBody);
        validate(settings);
        AIModerationSettings updated = aiModerationSettingsService.updateAIModerationSettings(server, settings);
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toAIModerationSettings(updated);
    }

    @GetMapping("/webhooks")
    public gg.modl.proto.modl.v1.WebhookSettings getWebhookSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        WebhookSettings settings = webhookSettingsService.getWebhookSettings(server);
        return PanelSettingsProtoMapper.toWebhookSettings(settings);
    }

    @PatchMapping("/webhooks")
    public gg.modl.proto.modl.v1.WebhookSettings updateWebhookSettings(
        @RequestBody UpdateWebhookSettingsRequest requestBody,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        WebhookSettings settings = PanelSettingsProtoMapper.fromUpdateWebhookSettingsRequest(requestBody);
        WebhookSettings updated = webhookSettingsService.updateWebhookSettings(server, settings);
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toWebhookSettings(updated);
    }

    @PostMapping("/webhooks/test")
    public WebhookTestResponse testWebhook(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        boolean success = webhookSettingsService.testWebhook(server);

        if (!success) {
            throw new ValidationException("Failed to send webhook test");
        }
        return PanelSettingsProtoMapper.toWebhookTestResponse("Webhook test sent successfully");
    }

    @GetMapping("/ticket-forms")
    public TicketFormSettingsEnvelope getTicketFormSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toTicketFormSettingsEnvelope(
            ticketFormSettingsService.getTicketFormSettingsState(server));
    }

    @PatchMapping("/ticket-forms")
    public TicketFormSettingsEnvelope patchTicketFormSettings(
        @RequestBody PatchTicketFormSettingsRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        VersionedSettings<TicketFormSettings> updated = ticketFormSettingsService.patchTicketFormSettings(
            server,
            body.getExpectedVersion(),
            PanelSettingsProtoMapper.fromTicketFormSettings(body.getSettings())
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toTicketFormSettingsEnvelope(updated);
    }

    @GetMapping("/ticket-forms/{type}")
    public ResponseEntity<TicketForm> getTicketForm(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketFormSettings.TicketForm form = ticketFormSettingsService.getFormByType(server, type);

        if (form == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PanelSettingsProtoMapper.toTicketForm(form));
    }

    @GetMapping("/quick-responses")
    public QuickResponseSettingsEnvelope getQuickResponses(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return PanelSettingsProtoMapper.toQuickResponseSettingsEnvelope(
            quickResponseSettingsService.getQuickResponseSettingsState(server));
    }

    @PatchMapping("/quick-responses")
    public QuickResponseSettingsEnvelope patchQuickResponses(
        @RequestBody PatchQuickResponsesRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        UpdateQuickResponsesRequest quickResponses = new UpdateQuickResponsesRequest(
            PanelSettingsProtoMapper.fromPatchQuickResponsesRequest(body));
        validate(quickResponses);
        VersionedSettings<QuickResponseSettings> updated = quickResponseSettingsService.patchQuickResponseSettings(
            server,
            body.getExpectedVersion(),
            quickResponses
        );
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toQuickResponseSettingsEnvelope(updated);
    }

    @PostMapping("/upload-icon")
    public ResponseEntity<?> uploadIcon(
        @RequestParam("icon") MultipartFile file,
        @RequestParam("iconType") String iconType,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> response = iconUploadService.uploadIcon(server, file, iconType);
        if (response.getStatusCode().is2xxSuccessful()) {
            invalidateSettings(server);
        }
        return response;
    }

    @PostMapping("/ai-apply-punishment/{ticketId}")
    public AISuggestionActionResponse applyAIPunishment(
        @PathVariable String ticketId,
        @RequestBody gg.modl.proto.modl.v1.ApplyAIPunishmentRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String email = RequestUtil.getSessionEmail(request);

        AITicketAnalysisService.AISuggestionResult result = aiTicketAnalysisService.applyAISuggestion(server, ticketId, email);
        return toAISuggestionResponse(result);
    }

    @PostMapping("/ai-dismiss-suggestion/{ticketId}")
    public AISuggestionActionResponse dismissAISuggestion(
        @PathVariable String ticketId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        AITicketAnalysisService.AISuggestionResult result = aiTicketAnalysisService.dismissAISuggestion(server, ticketId);
        return toAISuggestionResponse(result);
    }

    private AISuggestionActionResponse toAISuggestionResponse(AITicketAnalysisService.AISuggestionResult result) {
        return PanelSettingsProtoMapper.toAISuggestionActionResponse(result.success(), result.error(), null);
    }

    private void invalidateSettings(Server server) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_SETTINGS);
    }

    private void requireSuperAdmin(Server server, HttpServletRequest request) {
        String email = RequestUtil.getSessionEmail(request);
        if (!permissionService.isSuperAdmin(server, email)) {
            throw new ForbiddenException("Only super admins can manage API keys");
        }
    }

    private <T> void validate(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations.iterator().next().getMessage());
        }
    }

}
