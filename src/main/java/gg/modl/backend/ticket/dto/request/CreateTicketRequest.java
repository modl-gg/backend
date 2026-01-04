package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record CreateTicketRequest(
        @NotBlank String type,
        String subject,
        String description,
        String creatorUuid,
        String creatorName,
        String creatorEmail,
        String reportedPlayerUuid,
        String reportedPlayerName,
        List<Map<String, Object>> chatMessages,
        Map<String, Object> formData,
        List<String> tags,
        String priority,
        String creatorIdentifier
) {
}
