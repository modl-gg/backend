package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MinecraftClaimTicketRequest(
    @NotBlank String playerUuid,
    @NotBlank String playerName
) {
}
