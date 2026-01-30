package gg.modl.backend.settings.controller;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.TicketFormSettings;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.TicketFormSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    private final DynamicMongoTemplateProvider mongoProvider;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPublicSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        if (server == null) {
            return ResponseEntity.ok(getNotFoundSettings());
        }

        try {
            GeneralSettings generalSettings = generalSettingsService.getGeneralSettings(server);
            TicketFormSettings ticketForms = ticketFormSettingsService.getTicketFormSettings(server);

            // Get global maintenance status from system_config
            SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();

            Map<String, Object> response = new HashMap<>();
            response.put("serverExists", true);
            response.put("serverDisplayName", generalSettings.getServerDisplayName() != null
                    ? generalSettings.getServerDisplayName() : "modl");
            response.put("panelIconUrl", generalSettings.getPanelIconUrl());
            response.put("homepageIconUrl", generalSettings.getHomepageIconUrl());
            response.put("ticketForms", buildTicketFormsResponse(ticketForms));
            response.put("maintenanceMode", globalConfig.isMaintenanceMode());
            response.put("maintenanceMessage", globalConfig.getMaintenanceMessage());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(getNotFoundSettings());
        }
    }

    private Map<String, Object> getNotFoundSettings() {
        SystemConfig.GeneralConfig globalConfig = getGlobalMaintenanceConfig();

        Map<String, Object> response = new HashMap<>();
        response.put("serverExists", false);
        response.put("serverDisplayName", null);
        response.put("panelIconUrl", null);
        response.put("homepageIconUrl", null);
        response.put("ticketForms", Map.of());
        response.put("maintenanceMode", globalConfig.isMaintenanceMode());
        response.put("maintenanceMessage", globalConfig.getMaintenanceMessage());
        return response;
    }

    private SystemConfig.GeneralConfig getGlobalMaintenanceConfig() {
        try {
            Query query = Query.query(Criteria.where("configId").is("main_config"));
            SystemConfig config = mongoProvider.getGlobalDatabase()
                    .findOne(query, SystemConfig.class, "system_config");
            if (config != null && config.getGeneral() != null) {
                return config.getGeneral();
            }
        } catch (Exception e) {
            // If we can't get the global config, return defaults
        }
        return new SystemConfig.GeneralConfig();
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
