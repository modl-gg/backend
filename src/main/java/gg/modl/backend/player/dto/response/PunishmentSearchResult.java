package gg.modl.backend.player.dto.response;

import java.util.Date;

public record PunishmentSearchResult(
        String id,
        String playerName,
        int typeOrdinal,
        String status,
        Date issued
) {
}
