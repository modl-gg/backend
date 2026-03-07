package gg.modl.backend.player.dto.request;

import gg.modl.backend.validation.RegExpConstants;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AcknowledgeNotificationsRequest(
        @NotBlank
        @Pattern(regexp = RegExpConstants.UUID)
        String playerUuid,

        @NotEmpty
        @Size(max = RequestValidationLimits.NOTIFICATION_ACK_MAX_IDS)
        List<@NotBlank @Size(max = RequestValidationLimits.NOTIFICATION_ID_MAX_LENGTH) String> notificationIds,

        @Size(max = RequestValidationLimits.ACK_TIMESTAMP_MAX_LENGTH)
        String acknowledgedAt
) {
}