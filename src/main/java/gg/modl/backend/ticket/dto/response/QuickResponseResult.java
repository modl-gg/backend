package gg.modl.backend.ticket.dto.response;

public record QuickResponseResult(
        boolean success,
        String message,
        String ticketId,
        String actionName,
        boolean ticketClosed,
        boolean punishmentIssued,
        String appealOutcome
) {}
