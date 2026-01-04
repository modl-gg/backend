package gg.modl.backend.ticket.dto.response;

import gg.modl.backend.ticket.data.TicketReply;

import java.util.Date;

public record TicketListItemResponse(
        String id,
        String subject,
        String status,
        String reportedBy,
        String reportedByName,
        Date date,
        String category,
        boolean locked,
        String type,
        TicketReply lastReply,
        int replyCount
) {
}
