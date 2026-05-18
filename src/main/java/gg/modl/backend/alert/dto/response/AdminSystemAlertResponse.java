package gg.modl.backend.alert.dto.response;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import java.util.Date;

public record AdminSystemAlertResponse(
    String id,
    String message,
    SystemAlertSeverity severity,
    SystemAlertAudience audience,
    Date expiresAt,
    Date createdAt,
    Date updatedAt
) {
    public static AdminSystemAlertResponse from(SystemAlert alert) {
        return new AdminSystemAlertResponse(
            alert.getId(),
            alert.getMessage(),
            alert.getSeverity(),
            alert.getAudience(),
            alert.getExpiresAt(),
            alert.getCreatedAt(),
            alert.getUpdatedAt()
        );
    }
}
