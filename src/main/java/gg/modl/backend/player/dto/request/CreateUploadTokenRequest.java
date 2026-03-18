package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateUploadTokenRequest(
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId
) {}
