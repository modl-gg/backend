package gg.modl.backend.storage.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvidenceConfirmUploadRequest(
        @NotBlank(message = "Key is required")
        @Size(max = RequestValidationLimits.EVIDENCE_URL_MAX_LENGTH)
        String key
) {}
