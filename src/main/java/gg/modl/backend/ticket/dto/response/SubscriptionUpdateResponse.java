package gg.modl.backend.ticket.dto.response;

import java.util.Date;

public record SubscriptionUpdateResponse(
        String id,
        String ticketId,
        String ticketTitle,
        String replyContent,
        String replyBy,
        Date replyAt,
        boolean isStaffReply,
        boolean isRead,
        Integer additionalCount
) {
}
