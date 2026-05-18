package gg.modl.backend.alert.dto.response;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import java.util.Date;

public record PanelSystemAlertResponse(
    String id,
    String message,
    SystemAlertSeverity severity,
    SystemAlertAudience audience,
    Date expiresAt
) {
    public static PanelSystemAlertResponse from(SystemAlert alert) {
        return new PanelSystemAlertResponse(
            alert.getId(),
            alert.getMessage(),
            alert.getSeverity(),
            alert.getAudience(),
            alert.getExpiresAt()
        );
    }
}
