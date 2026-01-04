package gg.modl.backend.ticket.dto.response;

import java.util.Date;

public record TicketSubscriptionResponse(
        String ticketId,
        String ticketTitle,
        Date subscribedAt
) {
}
