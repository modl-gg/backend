package gg.modl.backend.storage.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record ConfirmUploadRequest(
    @NotBlank(message = "Key is required")
    @Size(max = RequestValidationLimits.STORAGE_KEY_MAX_LENGTH)
    String key,

    @Nullable @Size(max = RequestValidationLimits.STORAGE_ACCESS_TOKEN_MAX_LENGTH) String accessToken
) {}
