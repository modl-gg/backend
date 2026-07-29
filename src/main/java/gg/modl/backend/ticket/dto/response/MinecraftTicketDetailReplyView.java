package gg.modl.backend.ticket.dto.response;

import java.util.Date;

public record MinecraftTicketDetailReplyView(
    String id,
    String content,
    String authorName,
    String authorId,
    boolean isStaff,
    Date createdAt
) {
}
