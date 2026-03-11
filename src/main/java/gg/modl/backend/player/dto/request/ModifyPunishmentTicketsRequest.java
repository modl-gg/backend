package gg.modl.backend.player.dto.request;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record ModifyPunishmentTicketsRequest(
    @Nullable List<String> addTicketIds,
    @Nullable List<String> removeTicketIds,
    boolean modifyAssociatedTickets,
    @Nullable String issuerName,
    @Nullable String issuerId
) {
}
