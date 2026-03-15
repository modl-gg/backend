package gg.modl.backend.settings.dto.response;

import java.util.Map;

public record PublicSettingsResponse(
    boolean serverExists,
    String serverDisplayName,
    String panelIconUrl,
    String homepageIconUrl,
    Map<String, Object> ticketForms,
    boolean maintenanceMode,
    String maintenanceMessage
) {
}
