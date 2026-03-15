package gg.modl.backend.storage.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record EvidenceItemRequest(
    @NotBlank
    @Size(max = RequestValidationLimits.EVIDENCE_URL_MAX_LENGTH)
    String url,

    @NotBlank
    @Size(max = RequestValidationLimits.FILE_NAME_MAX_LENGTH)
    String fileName,

    @Size(max = RequestValidationLimits.CONTENT_TYPE_MAX_LENGTH)
    String fileType,

    @PositiveOrZero
    Long fileSize
) {
}