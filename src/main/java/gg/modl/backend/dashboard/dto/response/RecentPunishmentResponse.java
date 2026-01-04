package gg.modl.backend.dashboard.dto.response;

import java.util.Date;

public record RecentPunishmentResponse(
        String id,
        String playerName,
        String playerUuid,
        String type,
        String reason,
        String issuerName,
        Date issued,
        boolean active
) {
}
