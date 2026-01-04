package gg.modl.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignUploadRequest(
        @NotBlank(message = "Upload type is required")
        String uploadType,

        @NotBlank(message = "File name is required")
        @Size(max = 255, message = "File name too long")
        String fileName,

        @NotNull(message = "File size is required")
        @Positive(message = "File size must be positive")
        Long fileSize,

        @NotBlank(message = "Content type is required")
        String contentType
) {}
