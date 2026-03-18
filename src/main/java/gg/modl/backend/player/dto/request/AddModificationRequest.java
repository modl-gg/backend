package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

public record AddModificationRequest(
    @NotBlank @Size(max = RequestValidationLimits.PLAYER_MODIFICATION_TYPE_MAX_LENGTH) String type,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId,
    @Nullable @PositiveOrZero Long effectiveDuration,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_MODIFICATION_REASON_MAX_LENGTH) String reason,
    @Nullable @Size(max = RequestValidationLimits.ID_MAX_LENGTH) String appealTicketId
) {
}
