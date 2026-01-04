package gg.modl.backend.player.dto.response;

import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

public record PunishmentResponse(
        String id,
        String type,
        int typeOrdinal,
        String issuerName,
        Date issued,
        @Nullable Date started,
        boolean isAppealable,
        @Nullable String reason,
        @Nullable String severity,
        @Nullable String status,
        boolean active,
        @Nullable Date expires,
        @Nullable String playerUuid,
        @Nullable String playerUsername,
        @Nullable Boolean altBlocking,
        @Nullable Boolean statWiping,
        @Nullable String offenseLevel,
        List<PunishmentModification> modifications,
        List<PunishmentNote> notes,
        List<PunishmentEvidence> evidence,
        List<String> attachedTicketIds
) {
}
