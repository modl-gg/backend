package gg.modl.backend.ticket.dto.response;

import java.util.List;

public sealed interface MinecraftV1Response permits
    MinecraftV1Response.TicketCreated,
    MinecraftV1Response.TicketList,
    MinecraftV1Response.PlayerTicketList,
    MinecraftV1Response.TicketLookupList,
    MinecraftV1Response.TicketDetail,
    MinecraftV1Response.TicketClaim,
    MinecraftV1Response.ReportList,
    MinecraftV1Response.ReportOperation,
    MinecraftV1Response.NotFound {

    int status();

    record TicketCreated(int status, boolean success, String ticketId, String message) implements MinecraftV1Response {
    }

    record TicketList(int status, List<MinecraftTicketListItemView> tickets) implements MinecraftV1Response {
    }

    record PlayerTicketList(int status, List<MinecraftPlayerTicketView> tickets) implements MinecraftV1Response {
    }

    record TicketLookupList(int status, List<MinecraftTicketLookupView> tickets) implements MinecraftV1Response {
    }

    record TicketDetail(int status, MinecraftTicketDetailView ticket) implements MinecraftV1Response {
    }

    record TicketClaim(int status, boolean success, String message, String ticketId, String subject)
        implements MinecraftV1Response {
    }

    record ReportList(int status, List<MinecraftReportView> reports) implements MinecraftV1Response {
    }

    record ReportOperation(int status, boolean success, String message) implements MinecraftV1Response {
    }

    record NotFound(int status, String message) implements MinecraftV1Response {
    }
}
