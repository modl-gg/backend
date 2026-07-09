package gg.modl.backend.ticket.dto.response;

import java.util.Date;
import java.util.List;

public record PlayerTicketResponse(
    String id,
    String subject,
    String status,
    String type,
    Date created,
    String creatorName,
    String creatorUuid,
    String reportedPlayer,
    String reportedPlayerUuid,
    boolean locked,
    List<String> tags,
    List<String> assignedTo
) {
}
