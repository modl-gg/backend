package gg.modl.backend.storage.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EvidencePresignUploadRequest(
    @NotBlank(message = "File name is required")
    @Size(max = RequestValidationLimits.FILE_NAME_MAX_LENGTH)
    String fileName,

    @NotBlank(message = "Content type is required")
    @Size(max = RequestValidationLimits.CONTENT_TYPE_MAX_LENGTH)
    String contentType,

    @Positive(message = "File size must be positive")
    long fileSize
) {}
