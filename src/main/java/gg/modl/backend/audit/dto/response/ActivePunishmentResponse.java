package gg.modl.backend.audit.dto.response;

import java.util.Date;
import java.util.List;

public record ActivePunishmentResponse(
    String id,
    String playerId,
    String playerName,
    String type,
    int typeOrdinal,
    String category,
    String staffName,
    String reason,
    Long duration,
    Date issued,
    Date started,
    Date expires,
    boolean active,
    boolean hasEvidence,
    int evidenceCount,
    List<EvidenceItem> evidence,
    List<String> attachedTicketIds
) {
    public record EvidenceItem(
        String text,
        String url,
        String type,
        String fileName
    ) {}
}
