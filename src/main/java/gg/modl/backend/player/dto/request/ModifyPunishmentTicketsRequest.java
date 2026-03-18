package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public record ModifyPunishmentTicketsRequest(
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_TICKETS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.ID_MAX_LENGTH) String> addTicketIds,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_TICKETS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.ID_MAX_LENGTH) String> removeTicketIds,
    boolean modifyAssociatedTickets,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId
) {
}
