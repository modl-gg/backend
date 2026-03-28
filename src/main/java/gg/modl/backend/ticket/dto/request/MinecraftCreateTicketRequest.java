package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record MinecraftCreateTicketRequest(
    @NotBlank @Pattern(regexp = RegExpConstants.UUID) String creatorUuid,
    @Nullable @Pattern(regexp = RegExpConstants.MINECRAFT_USERNAME) @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String creatorName,
    @NotBlank
    @Pattern(regexp = "(?i)^(bug|bug[ _-]?report|player|player[ _-]?report|chat|chat[ _-]?report|appeal|ban[ _-]?appeal|application|staff|staff[ _-]?application|apply|support|general[ _-]?support)$")
    String type,
    @Nullable @Size(max = RequestValidationLimits.TICKET_SUBJECT_MAX_LENGTH) String subject,
    @Nullable @Size(max = RequestValidationLimits.TICKET_DESCRIPTION_MAX_LENGTH) String description,
    @Nullable @Pattern(regexp = RegExpConstants.UUID) String reportedPlayerUuid,
    @Nullable @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String reportedPlayerName,
    @Nullable @Size(max = RequestValidationLimits.MC_TICKET_CHAT_MESSAGES_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.CHAT_LOG_MESSAGE_MAX_LENGTH) String> chatMessages,
    @Nullable @Size(max = RequestValidationLimits.TICKET_TAGS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String> tags,
    @Nullable @Pattern(regexp = "(?i)^(low|minor|normal|medium|default|standard|high|urgent|critical|highest)$")
    String priority,
    @Nullable @Size(max = RequestValidationLimits.MC_CREATE_TICKET_SERVER_MAX_LENGTH) String createdServer,
    @Nullable @Size(max = RequestValidationLimits.MC_CREATE_TICKET_REPLAY_URL_MAX_LENGTH) String replayUrl
) {
}
