package gg.modl.backend.staff.dto.request;

import gg.modl.backend.validation.RegExpConstants;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record AssignMinecraftPlayerRequest(
    @Nullable @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
    @Nullable @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String minecraftUsername
) {
}
