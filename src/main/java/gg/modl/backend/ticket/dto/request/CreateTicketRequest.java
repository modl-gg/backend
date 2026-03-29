package gg.modl.backend.ticket.dto.request;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

public record CreateTicketRequest(
    @NotBlank
    @Pattern(regexp = "(?i)^(bug|bug[ _-]?report|player|player[ _-]?report|chat|chat[ _-]?report|appeal|ban[ _-]?appeal|application|staff|staff[ _-]?application|apply|support|general[ _-]?support)$")
    String type,
    @Nullable @Size(max = RequestValidationLimits.TICKET_SUBJECT_MAX_LENGTH) String subject,
    @Nullable @Size(max = RequestValidationLimits.TICKET_DESCRIPTION_MAX_LENGTH) String description,
    @Nullable @Pattern(regexp = RegExpConstants.UUID) String creatorUuid,
    @Nullable @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String creatorName,
    @Nullable @Email @Size(min = 3, max = RequestValidationLimits.EMAIL_MAX_LENGTH) String creatorEmail,
    @Nullable @Pattern(regexp = RegExpConstants.UUID) String reportedPlayerUuid,
    @Nullable @Size(max = RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) String reportedPlayerName,
    @Nullable @Size(max = RequestValidationLimits.TICKET_CHAT_MESSAGES_MAX_ENTRIES) List<Map<String, Object>> chatMessages,
    @Nullable @Size(max = RequestValidationLimits.TICKET_FORM_DATA_MAX_ENTRIES) Map<String, Object> formData,
    @Nullable @Size(max = RequestValidationLimits.TICKET_ATTACHMENTS_MAX_ENTRIES) List<Object> attachments,
    @Nullable @Size(max = RequestValidationLimits.TICKET_TAGS_MAX_ENTRIES) List<@Size(max = RequestValidationLimits.TICKET_TAG_MAX_LENGTH) String> tags,
    @Nullable @Pattern(regexp = "(?i)^(low|minor|normal|medium|default|standard|high|urgent|critical|highest)$")
    String priority,
    @Nullable @Size(max = RequestValidationLimits.TICKET_CREATOR_IDENTIFIER_MAX_LENGTH) String creatorIdentifier,
    @Nullable Boolean emailAuthEnabled,
    @Nullable @Size(max = RequestValidationLimits.TICKET_FORM_DATA_MAX_ENTRIES) Map<String, String> fieldLabels
) {
}
