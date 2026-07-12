package gg.modl.backend.ticket.dto.response;

import gg.modl.backend.ticket.data.Ticket;
import java.util.Date;
import java.util.List;

public record MinecraftTicketDetailView(
    String id,
    String type,
    String category,
    String subject,
    String status,
    String playerName,
    String playerUuid,
    String priority,
    List<String> assignedTo,
    Date createdAt,
    Date updatedAt,
    boolean locked,
    List<MinecraftTicketDetailReplyView> replies,
    List<Ticket.ChatMessage> chatMessages,
    String replayUrl
) {
}
