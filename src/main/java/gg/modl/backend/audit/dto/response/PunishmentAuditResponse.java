package gg.modl.backend.audit.dto.response;

import java.util.Date;

public record PunishmentAuditResponse(
        String id,
        String type,
        String playerId,
        String playerName,
        String staffId,
        String staffName,
        String reason,
        String duration,
        Date timestamp,
        boolean canRollback
) {
}
