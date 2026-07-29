package gg.modl.backend.ticket.dto.response;

import java.util.Date;

public record MinecraftTicketLookupView(
    String id,
    String type,
    String category,
    String subject,
    String status,
    String playerName,
    String playerUuid,
    Date createdAt,
    String firstReplyContent
) {
}
