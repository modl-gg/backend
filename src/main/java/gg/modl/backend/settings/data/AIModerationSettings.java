package gg.modl.backend.settings.data;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIModerationSettings {
    private boolean enableAIReview;
    private boolean enableAutomatedActions;
    @Builder.Default
    @Size(max = RequestValidationLimits.AI_PUNISHMENT_CONFIGS_MAX_ENTRIES)
    private Map<String, @Valid AIPunishmentConfig> aiPunishmentConfigs = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIPunishmentConfig {
        @NotBlank
        @Size(max = RequestValidationLimits.AI_PUNISHMENT_ID_MAX_LENGTH)
        private String id;
        @NotBlank
        @Size(max = RequestValidationLimits.AI_PUNISHMENT_NAME_MAX_LENGTH)
        private String name;
        @NotBlank
        @Size(max = RequestValidationLimits.AI_PUNISHMENT_DESCRIPTION_MAX_LENGTH)
        private String aiDescription;
        private boolean enabled;
    }
}
