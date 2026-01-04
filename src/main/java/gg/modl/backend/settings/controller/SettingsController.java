package gg.modl.backend.settings.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.*;
import gg.modl.backend.settings.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiredArgsConstructor
public class SettingsController {
    private final PunishmentTypeService punishmentTypeService;
    private final GeneralSettingsService generalSettingsService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final AIModerationSettingsService aiModerationSettingsService;
    private final WebhookSettingsService webhookSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;

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

        if (ordinal < 6) {
            return ResponseEntity.badRequest().build();
        }

        try {
            PunishmentType result = punishmentTypeService.updatePunishmentType(server, ordinal, updatedType);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/punishment-types/reset")
    public ResponseEntity<List<PunishmentType>> resetPunishmentTypes(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<PunishmentType> types = punishmentTypeService.initializeDefaultTypes(server);
        return ResponseEntity.ok(types);
    }

    @GetMapping("/general")
    public ResponseEntity<GeneralSettings> getGeneralSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        GeneralSettings settings = generalSettingsService.getGeneralSettings(server);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/general")
    public ResponseEntity<GeneralSettings> updateGeneralSettings(
            @RequestBody GeneralSettings settings,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        GeneralSettings updated = generalSettingsService.updateGeneralSettings(server, settings);
        return ResponseEntity.ok(updated);
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
}
