package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configurable thresholds for offender levels based on points.
 * Used to determine if a player is Low, Medium, or Habitual offender.
 * Separate thresholds for Social and Gameplay categories.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffenderThresholdSettings {
    /**
     * Thresholds for social category punishments.
     * Default: medium=4, habitual=8
     */
    @Builder.Default
    private CategoryThresholds social = new CategoryThresholds(4, 8);

    /**
     * Thresholds for gameplay category punishments.
     * Default: medium=5, habitual=10
     */
    @Builder.Default
    private CategoryThresholds gameplay = new CategoryThresholds(5, 10);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryThresholds {
        /**
         * Points threshold for Medium offender level.
         * Players with points >= this value and < habitual are "Medium".
         */
        private int medium;

        /**
         * Points threshold for Habitual offender level.
         * Players with points >= this value are "Habitual".
         */
        private int habitual;

        /**
         * Calculate the offender level based on points.
         * @param points The player's offense points
         * @return "Low", "Medium", or "Habitual"
         */
        public String getOffenderLevel(int points) {
            if (points >= habitual) {
                return "Habitual";
            } else if (points >= medium) {
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
            if (points >= habitual) {
                return "habitual";
            } else if (points >= medium) {
                return "medium";
            } else {
                return "first";
            }
        }
    }

    /**
     * Get the social offender level for display.
     */
    public String getSocialOffenderLevel(int points) {
        return social.getOffenderLevel(points);
    }

    /**
     * Get the gameplay offender level for display.
     */
    public String getGameplayOffenderLevel(int points) {
        return gameplay.getOffenderLevel(points);
    }

    /**
     * Get the internal offense level for a category (for duration lookup).
     */
    public String getOffenseLevelInternal(int points, boolean isSocial) {
        return isSocial ? social.getOffenseLevelInternal(points) : gameplay.getOffenseLevelInternal(points);
    }

    public static OffenderThresholdSettings defaults() {
        return OffenderThresholdSettings.builder().build();
    }
}
