package gg.modl.backend.ticket.dto.response;

import java.util.Date;
import java.util.List;

public record MinecraftTicketListItemView(
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
    boolean hasStaffResponse,
    int replyCount,
    boolean locked
) {
}
