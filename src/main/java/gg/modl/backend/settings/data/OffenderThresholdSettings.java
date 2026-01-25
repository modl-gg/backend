package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configurable thresholds for offender levels based on points.
 * Used to determine if a player is Low, Medium, or Habitual offender.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffenderThresholdSettings {
    /**
     * Points threshold for Medium offender level.
     * Players with points >= this value and < habitualThreshold are "Medium".
     * Players with points < this value are "Low".
     * Default: 1
     */
    @Builder.Default
    private int mediumThreshold = 1;

    /**
     * Points threshold for Habitual offender level.
     * Players with points >= this value are "Habitual".
     * Default: 3
     */
    @Builder.Default
    private int habitualThreshold = 3;

    /**
     * Calculate the offender level based on points.
     * @param points The player's offense points (social or gameplay)
     * @return "Low", "Medium", or "Habitual"
     */
    public String getOffenderLevel(int points) {
        if (points >= habitualThreshold) {
            return "Habitual";
        } else if (points >= mediumThreshold) {
            return "Medium";
        } else {
            return "Low";
        }
    }

    /**
     * Get the internal offense level used for duration lookup.
     * @param points The player's offense points
     * @return "first", "medium", or "habitual" (for duration matrix lookup)
     */
    public String getOffenseLevelInternal(int points) {
        if (points >= habitualThreshold) {
            return "habitual";
        } else if (points >= mediumThreshold) {
            return "medium";
        } else {
            return "first";
        }
    }

    public static OffenderThresholdSettings defaults() {
        return OffenderThresholdSettings.builder().build();
    }
}
