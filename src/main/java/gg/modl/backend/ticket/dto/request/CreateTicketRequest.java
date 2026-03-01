package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateTicketRequest(
        @NotBlank String type,
        String subject,
        String description,
        String creatorUuid,
        String creatorName,
        @Email @Size(min = 3, max = 254) String creatorEmail,
        String reportedPlayerUuid,
        String reportedPlayerName,
        List<Map<String, Object>> chatMessages,
        Map<String, Object> formData,
        List<Object> attachments,
        List<String> tags,
        String priority,
        String creatorIdentifier,
        Boolean emailAuthEnabled
) {
}
