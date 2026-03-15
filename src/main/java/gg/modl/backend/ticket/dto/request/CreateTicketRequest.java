package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateTicketRequest(
    @NotBlank
    @Pattern(regexp = "(?i)^(bug|bug[ _-]?report|player|player[ _-]?report|chat|chat[ _-]?report|appeal|ban[ _-]?appeal|application|staff|staff[ _-]?application|apply|support|general[ _-]?support)$")
    String type,
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
    @Pattern(regexp = "(?i)^(low|minor|normal|medium|default|standard|high|urgent|critical|highest)$")
    String priority,
    String creatorIdentifier,
    Boolean emailAuthEnabled
) {
}
