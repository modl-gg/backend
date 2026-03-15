package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RegExpConstants;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CommandLogEntryRequest(
    @NotBlank
    @Pattern(regexp = RegExpConstants.UUID)
    String uuid,

    @NotBlank
    @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME)
    @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH)
    String username,

    @NotBlank
    @Size(max = RequestValidationLimits.COMMAND_LOG_MAX_LENGTH)
    String command,

    @PositiveOrZero
    long timestamp,

    @Size(max = RequestValidationLimits.LOG_SERVER_NAME_MAX_LENGTH)
    String server
) {
}