package gg.modl.backend.settings.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIModerationSettings {
    private boolean enableAIReview;
    private boolean enableAutomatedActions;
    private String strictnessLevel;
    private Map<String, AIPunishmentConfig> aiPunishmentConfigs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIPunishmentConfig {
        private String id;
        private String name;
        private String aiDescription;
        private boolean enabled;
    }
}
