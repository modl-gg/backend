package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public record CreatePunishmentRequest(
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId,
    @NotNull @PositiveOrZero Integer typeOrdinal,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_NOTES_MAX_ENTRIES) List<@Valid CreateNoteRequest> notes,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_EVIDENCE_MAX_ENTRIES) List<@Valid CreateEvidenceRequest> evidence,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_TICKETS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String> attachedTicketIds,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_SEVERITY_MAX_LENGTH) String severity,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_STATUS_MAX_LENGTH) String status,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_DATA_MAX_ENTRIES) Map<String, Object> data,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH) String reason,
    @Nullable @PositiveOrZero Long duration
) {
}
