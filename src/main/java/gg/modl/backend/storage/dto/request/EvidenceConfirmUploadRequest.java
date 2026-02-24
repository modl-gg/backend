package gg.modl.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EvidenceConfirmUploadRequest(
        @NotBlank(message = "Key is required")
        String key
) {}
