package gg.modl.backend.ticket.dto.request;

import java.util.List;

public record MinecraftTicketsByIdsRequest(
    List<String> ids
) {
}
