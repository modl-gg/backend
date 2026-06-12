package gg.modl.backend.settings.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.admin.service.GlobalSystemService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import gg.modl.proto.modl.v1.PublicSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_SETTINGS)
@RequiredArgsConstructor
public class PublicSettingsController {
    private final GeneralSettingsService generalSettingsService;
    private final TicketFormSettingsService ticketFormSettingsService;
    private final GlobalSystemService globalSystemService;

    @GetMapping
    public PublicSettingsResponse getPublicSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        if (server == null) {
            return getNotFoundSettings();
        }

        GeneralSettings generalSettings = generalSettingsService.getGeneralSettings(server);
        TicketFormSettings ticketForms = ticketFormSettingsService.getTicketFormSettings(server);
        SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();
        return PanelSettingsProtoMapper.toPublicSettingsResponse(new gg.modl.backend.settings.dto.response.PublicSettingsResponse(
            true,
            generalSettings.getServerDisplayName() != null ? generalSettings.getServerDisplayName() : "modl",
            generalSettings.getPanelIconUrl(),
            generalSettings.getHomepageIconUrl(),
            ticketFormSettingsService.buildTicketFormsResponse(ticketForms),
            globalConfig.isMaintenanceMode(),
            globalConfig.getMaintenanceMessage()
        ));
    }

    private PublicSettingsResponse getNotFoundSettings() {
        SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();
        return PanelSettingsProtoMapper.toPublicSettingsResponse(new gg.modl.backend.settings.dto.response.PublicSettingsResponse(
            false,
            null,
            null,
            null,
            Map.of(),
            globalConfig.isMaintenanceMode(),
            globalConfig.getMaintenanceMessage()
        ));
    }

    private SystemConfig.GeneralConfig getGlobalMaintenanceConfig() {
        return globalSystemService.getGeneralConfigOrDefault();
    }
}
