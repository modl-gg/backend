package gg.modl.backend.storage.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkDeleteRequest(
    @NotEmpty(message = "No keys provided")
    List<String> keys
) {}
