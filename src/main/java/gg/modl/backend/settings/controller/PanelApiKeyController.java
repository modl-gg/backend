package gg.modl.backend.settings.controller;

import gg.modl.backend.infrastructure.authorization.PanelAccessRule;
import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import gg.modl.proto.modl.v1.ApiKeyDeleteResponse;
import gg.modl.proto.modl.v1.ApiKeyExistsResponse;
import gg.modl.proto.modl.v1.ApiKeyGenerateResponse;
import gg.modl.proto.modl.v1.ApiKeyRevealResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiresPanelPermission(rule = PanelAccessRule.SUPER_ADMIN)
@RequiredArgsConstructor
public class PanelApiKeyController {
    private final ApiKeySettingsService apiKeySettingsService;
    private final SettingsInvalidationPublisher settingsInvalidationPublisher;

    @PostMapping("/api-keys/{type}/generate")
    public ApiKeyGenerateResponse generateApiKey(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String apiKey = apiKeySettingsService.generateApiKey(server, type);
        settingsInvalidationPublisher.invalidateSettings(server);
        return PanelSettingsProtoMapper.toApiKeyGenerateResponse("API key generated successfully", apiKey);
    }

    @GetMapping("/api-keys/{type}/reveal")
    public ResponseEntity<ApiKeyRevealResponse> revealApiKey(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
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
        boolean deleted = apiKeySettingsService.deleteApiKey(server, type);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        settingsInvalidationPublisher.invalidateSettings(server);
        return ResponseEntity.ok(PanelSettingsProtoMapper.toApiKeyDeleteResponse("API key deleted successfully"));
    }

    @GetMapping("/api-keys/{type}/exists")
    public ApiKeyExistsResponse checkApiKeyExists(
        @PathVariable String type,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean exists = apiKeySettingsService.hasApiKey(server, type);
        return PanelSettingsProtoMapper.toApiKeyExistsResponse(exists);
    }
}
