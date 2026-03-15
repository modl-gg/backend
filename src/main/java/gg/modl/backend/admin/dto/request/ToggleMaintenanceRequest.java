package gg.modl.backend.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record ToggleMaintenanceRequest(
    @NotNull Boolean enabled,
    @Nullable String message
) {}
