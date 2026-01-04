package gg.modl.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmUploadRequest(
        @NotBlank(message = "Key is required")
        String key
) {}
