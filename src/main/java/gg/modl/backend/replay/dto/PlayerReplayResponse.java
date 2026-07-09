package gg.modl.backend.replay.dto;

import gg.modl.backend.ticket.data.Ticket;
import java.util.Date;

public record PlayerReplayResponse(
    String replayId,
    String targetUuid,
    String targetName,
    String mcVersion,
    long fileSize,
    Date createdAt,
    String status,
    String replayUrl,
    MatchSource matchSource
) {
    public enum MatchSource {
        DIRECT_METADATA,
        TICKET_FALLBACK
    }

    public static PlayerReplayResponse fromTicket(Ticket ticket, String replayUrl, String replayId) {
        return new PlayerReplayResponse(
            replayId,
            firstNonBlank(ticket.getReportedPlayerUuid(), ticket.getCreatorUuid()),
            firstNonBlank(ticket.getReportedPlayer(), ticket.getCreatorName()),
            null,
            0L,
            ticket.getCreated(),
            null,
            replayUrl,
            MatchSource.TICKET_FALLBACK
        );
    }

    public String deduplicationKey() {
        if (replayId != null && !replayId.isBlank()) {
            return "id:" + replayId;
        }
        return replayUrl != null ? "url:" + replayUrl : "empty";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
