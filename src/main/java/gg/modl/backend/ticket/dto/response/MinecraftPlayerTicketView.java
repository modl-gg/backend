package gg.modl.backend.ticket.dto.response;

import java.util.Date;

public record MinecraftPlayerTicketView(
    String id,
    String type,
    String category,
    String subject,
    String status,
    Date createdAt
) {
}
