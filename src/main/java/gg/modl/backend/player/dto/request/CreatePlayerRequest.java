package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RegExpConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePlayerRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.UUID) String minecraftUuid,
        @NotBlank @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) String username
) {
}
