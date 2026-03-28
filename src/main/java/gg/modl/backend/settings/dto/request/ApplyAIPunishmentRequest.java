package gg.modl.backend.settings.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record ApplyAIPunishmentRequest(
    @Nullable
    @Size(max = RequestValidationLimits.STAFF_USERNAME_MAX_LENGTH)
    String staffName
) {}
