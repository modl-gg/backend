package gg.modl.backend.player.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ModifyPunishmentTicketsRequest(
        @Nullable List<String> addTicketIds,
        @Nullable List<String> removeTicketIds,
        boolean modifyAssociatedTickets,
        @Nullable String issuerName,
        @Nullable String issuerId
) {
}
