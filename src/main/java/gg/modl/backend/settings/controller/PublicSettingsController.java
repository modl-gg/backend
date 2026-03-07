package gg.modl.backend.settings.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.service.GlobalSystemService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.dto.response.PublicSettingsResponse;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_SETTINGS)
@RequiredArgsConstructor
public class PublicSettingsController {
    private final GeneralSettingsService generalSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final GlobalSystemService globalSystemService;

    @GetMapping
    public ResponseEntity<PublicSettingsResponse> getPublicSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        if (server == null) {
            return ResponseEntity.ok(getNotFoundSettings());
        }

        try {
            GeneralSettings generalSettings = generalSettingsService.getGeneralSettings(server);
            TicketFormSettings ticketForms = ticketFormSettingsService.getTicketFormSettings(server);
            SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();
            return ResponseEntity.ok(new PublicSettingsResponse(
                    true,
                    generalSettings.getServerDisplayName() != null ? generalSettings.getServerDisplayName() : "modl",
                    generalSettings.getPanelIconUrl(),
                    generalSettings.getHomepageIconUrl(),
                    buildTicketFormsResponse(ticketForms),
                    globalConfig.isMaintenanceMode(),
                    globalConfig.getMaintenanceMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(getNotFoundSettings());
        }
    }

    private PublicSettingsResponse getNotFoundSettings() {
        SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();
        return new PublicSettingsResponse(
                false,
                null,
                null,
                null,
                Map.of(),
                globalConfig.isMaintenanceMode(),
                globalConfig.getMaintenanceMessage()
        );
    }

    private SystemConfig.GeneralConfig getGlobalMaintenanceConfig() {
        return globalSystemService.getGeneralConfigOrDefault();
    }

    private Map<String, Object> buildTicketFormsResponse(TicketFormSettings ticketForms) {
        Map<String, Object> forms = new HashMap<>();

        if (ticketForms.getBug() != null) {
            forms.put("bug", ticketForms.getBug());
        }
        if (ticketForms.getSupport() != null) {
            forms.put("support", ticketForms.getSupport());
        }
        if (ticketForms.getApplication() != null) {
            forms.put("application", ticketForms.getApplication());
        }
        if (ticketForms.getPlayer() != null) {
            forms.put("player", ticketForms.getPlayer());
        }
        if (ticketForms.getChat() != null) {
            forms.put("chat", ticketForms.getChat());
        }

        return forms;
    }
}
