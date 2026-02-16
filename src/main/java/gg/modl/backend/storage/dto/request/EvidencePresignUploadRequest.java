package gg.modl.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record EvidencePresignUploadRequest(
        @NotBlank(message = "File name is required")
        String fileName,

        @NotBlank(message = "Content type is required")
        String contentType,

        @Positive(message = "File size must be positive")
        long fileSize
) {}
