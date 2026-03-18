package gg.modl.backend.admin.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record ToggleMaintenanceRequest(
    @NotNull Boolean enabled,
    @Nullable @Size(max = RequestValidationLimits.MAINTENANCE_MESSAGE_MAX_LENGTH) String message
) {}
