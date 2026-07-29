package gg.modl.backend.ticket.dto.response;

import gg.modl.backend.ticket.data.Ticket;
import java.util.Date;
import java.util.List;

public record MinecraftReportView(
    String id,
    String type,
    String reporterName,
    String reporterUuid,
    String reportedPlayerUuid,
    String reportedPlayerName,
    String subject,
    String content,
    String status,
    String priority,
    Date createdAt,
    List<String> assignedTo,
    List<Ticket.ChatMessage> chatMessages,
    String replayUrl
) {
}
