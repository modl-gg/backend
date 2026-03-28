package gg.modl.backend.player.dto.request;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddUsernameRequest(
    @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username
) {
}
