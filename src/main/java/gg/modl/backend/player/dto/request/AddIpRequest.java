package gg.modl.backend.player.dto.request;

import gg.modl.backend.infrastructure.validation.ValidIpAddress;
import jakarta.validation.constraints.NotBlank;

public record AddIpRequest(
    @NotBlank @ValidIpAddress String ipAddress
) {
}
