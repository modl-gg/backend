package gg.modl.backend.storage.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record PresignUploadRequest(
    @NotBlank(message = "Upload type is required")
    @Size(max = RequestValidationLimits.STORAGE_UPLOAD_TYPE_MAX_LENGTH)
    String uploadType,

    @NotBlank(message = "File name is required")
    @Size(max = RequestValidationLimits.FILE_NAME_MAX_LENGTH)
    String fileName,

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    Long fileSize,

    @NotBlank(message = "Content type is required")
    @Size(max = RequestValidationLimits.CONTENT_TYPE_MAX_LENGTH)
    String contentType,

    @Nullable @Size(max = RequestValidationLimits.STORAGE_ENTITY_ID_MAX_LENGTH) String entityId,

    @Nullable @Size(max = RequestValidationLimits.STORAGE_ACCESS_TOKEN_MAX_LENGTH) String accessToken
) {}
