package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

public record CreateEvidenceRequest(
    @NotBlank @Size(max = RequestValidationLimits.EVIDENCE_TEXT_MAX_LENGTH) String text,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_NAME_MAX_LENGTH) String issuerName,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_ISSUER_ID_MAX_LENGTH) String issuerId,
    @Nullable @Size(max = RequestValidationLimits.ACK_TIMESTAMP_MAX_LENGTH) String date,
    @Nullable @Size(max = RequestValidationLimits.PLAYER_MODIFICATION_TYPE_MAX_LENGTH) String type,
    @Nullable @Size(max = RequestValidationLimits.EVIDENCE_URL_MAX_LENGTH) String fileUrl,
    @Nullable @Size(max = RequestValidationLimits.FILE_NAME_MAX_LENGTH) String fileName,
    @Nullable @Size(max = RequestValidationLimits.CONTENT_TYPE_MAX_LENGTH) String fileType,
    @Nullable @PositiveOrZero Long fileSize
) {
}
