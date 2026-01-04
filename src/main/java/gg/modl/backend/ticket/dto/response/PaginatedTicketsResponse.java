package gg.modl.backend.ticket.dto.response;

import java.util.List;

public record PaginatedTicketsResponse(
        List<TicketListItemResponse> tickets,
        PaginationInfo pagination,
        FiltersInfo filters
) {
    public record PaginationInfo(
            int current,
            int total,
            int limit,
            long totalTickets,
            boolean hasNext,
            boolean hasPrev
    ) {
    }

    public record FiltersInfo(
            String search,
            String status,
            String type
    ) {
    }
}
