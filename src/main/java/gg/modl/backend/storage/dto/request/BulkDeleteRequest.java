package gg.modl.backend.storage.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkDeleteRequest(
    @NotEmpty(message = "No keys provided")
    @Size(max = RequestValidationLimits.STORAGE_BULK_DELETE_MAX_KEYS)
    List<@NotBlank @Size(max = RequestValidationLimits.STORAGE_KEY_MAX_LENGTH) String> keys
) {}
