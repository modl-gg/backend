package gg.modl.backend.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record MinecraftCreateTicketRequest(
    @NotBlank String creatorUuid,
    String creatorName,
    @NotBlank
    @Pattern(regexp = "(?i)^(bug|bug[ _-]?report|player|player[ _-]?report|chat|chat[ _-]?report|appeal|ban[ _-]?appeal|application|staff|staff[ _-]?application|apply|support|general[ _-]?support)$")
    String type,
    String subject,
    String description,
    String reportedPlayerUuid,
    String reportedPlayerName,
    List<String> chatMessages,
    List<String> tags,
    @Pattern(regexp = "(?i)^(low|minor|normal|medium|default|standard|high|urgent|critical|highest)$")
    String priority,
    String createdServer
) {
}
