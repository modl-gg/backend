package gg.modl.backend.ticket.dto.request;

import java.util.List;

public record BulkTicketUpdateRequest(
        List<String> ticketIds,
        Boolean locked,
        List<String> addLabels,
        List<String> removeLabels,
        String assignTo
) {
}
