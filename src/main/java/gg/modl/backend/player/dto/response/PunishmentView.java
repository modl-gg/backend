package gg.modl.backend.player.dto.response;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record PunishmentView(
    String id,
    String issuerName,
    @Nullable Date issued,
    @Nullable Date started,
    int typeOrdinal,
    String type,
    List<Map<String, Object>> modifications,
    List<Map<String, Object>> notes,
    List<Map<String, Object>> evidence,
    List<String> attachedTicketIds,
    Map<String, Object> data,
    @Nullable String playerUuid,
    @Nullable String playerName
) {
    public PunishmentView withPlayer(@Nullable String playerUuid, @Nullable String playerName) {
        return new PunishmentView(id, issuerName, issued, started, typeOrdinal, type,
            modifications, notes, evidence, attachedTicketIds, data, playerUuid, playerName);
    }
}
