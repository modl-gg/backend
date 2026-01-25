package gg.modl.backend.player.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Response containing punishment preview calculations.
 * Shows what the punishment would be before issuing.
 */
@Data
@Builder
public class PunishmentPreviewResponse {
    private final int status;
    private final boolean success;
    private final String message;

    // Player's current status
    private final String socialStatus;
    private final String gameplayStatus;
    private final int socialPoints;
    private final int gameplayPoints;

    // Offense level calculation
    private final String offenseLevel; // "first", "medium", or "habitual"

    // Punishment preview for each severity
    private final SeverityPreview lenient;
    private final SeverityPreview regular;
    private final SeverityPreview aggravated;

    // For single-severity punishments
    private final SeverityPreview singleSeverity;

    // Punishment type info
    private final boolean singleSeverityPunishment;
    private final boolean permanentUntilUsernameChange;
    private final boolean permanentUntilSkinChange;
    private final boolean canBeAltBlocking;
    private final boolean canBeStatWiping;
    private final String category;

    @Data
    @Builder
    public static class SeverityPreview {
        private final String severity; // "lenient", "regular", "aggravated"
        private final int points;
        private final long durationMs;
        private final String durationFormatted;
        private final String punishmentType; // "ban", "mute", "kick"
        private final boolean permanent;

        // New status after this punishment
        private final String newSocialStatus;
        private final String newGameplayStatus;
        private final int newSocialPoints;
        private final int newGameplayPoints;
    }

    public static PunishmentPreviewResponse error(String message) {
        return PunishmentPreviewResponse.builder()
                .status(400)
                .success(false)
                .message(message)
                .build();
    }
}
