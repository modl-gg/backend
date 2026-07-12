package gg.modl.backend.player.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record MinecraftCreatePunishmentRequest(
    @NotBlank @Pattern(regexp = RegExpConstants.UUID) String targetUuid,
    @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId,
    @JsonProperty("type_ordinal") @Min(0) int typeOrdinal,
    @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH) String reason,
    @Min(0) Long duration,
    @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_DATA_MAX_ENTRIES) Map<String, Object> data,
    @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_NOTES_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.PLAYER_NOTE_TEXT_MAX_LENGTH) String> notes,
    @Size(max = RequestValidationLimits.PLAYER_PUNISHMENT_TICKETS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.ID_MAX_LENGTH) String> attachedTicketIds,
    @Size(max = RequestValidationLimits.PLAYER_SEVERITY_MAX_LENGTH) String severity,
    @Size(max = RequestValidationLimits.PLAYER_STATUS_MAX_LENGTH) String status
) {
}
