package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MinecraftCreateTicketRequest(
        @NotBlank String creatorUuid,
        String creatorName,
        @NotBlank String type,
        String subject,
        String description,
        String reportedPlayerUuid,
        String reportedPlayerName,
        List<String> chatMessages,
        List<String> tags,
        String priority,
        String createdServer
) {
}
