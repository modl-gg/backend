package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MinecraftClaimTicketRequest(
    @NotBlank @Pattern(regexp = RegExpConstants.UUID) String playerUuid,
    @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String playerName
) {
}
