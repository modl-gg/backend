package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RegExpConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddIpRequest(
        @NotBlank @Pattern(regexp = RegExpConstants.IP) String ipAddress
) {
}
