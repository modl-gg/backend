package gg.modl.backend.settings.dto.request;

import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record UpdateAIModerationSettingsRequest(
        Boolean enableAIReview,
        Boolean enableAutomatedActions,
        @NotBlank
        @Pattern(regexp = RequestValidationLimits.AI_STRICTNESS_LEVEL)
        String strictnessLevel,
        @Size(max = RequestValidationLimits.AI_PUNISHMENT_CONFIGS_MAX_ENTRIES)
        Map<
                @Pattern(regexp = RequestValidationLimits.NUMERIC_IDENTIFIER) String,
                @Valid AIPunishmentConfigRequest
                > aiPunishmentConfigs
) {
    public AIModerationSettings toSettings() {
        return AIModerationSettings.builder()
                .enableAIReview(Boolean.TRUE.equals(enableAIReview))
                .enableAutomatedActions(Boolean.TRUE.equals(enableAutomatedActions))
                .strictnessLevel(strictnessLevel)
                .aiPunishmentConfigs(toPunishmentConfigs())
                .build();
    }

    private Map<String, AIModerationSettings.AIPunishmentConfig> toPunishmentConfigs() {
        if (aiPunishmentConfigs == null) {
            return null;
        }

        return aiPunishmentConfigs.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue().toConfig() : null,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    public record AIPunishmentConfigRequest(
            @NotBlank
            @Pattern(regexp = RequestValidationLimits.NUMERIC_IDENTIFIER)
            String id,
            @NotBlank
            @Size(max = RequestValidationLimits.AI_PUNISHMENT_NAME_MAX_LENGTH)
            String name,
            @NotBlank
            @Size(max = RequestValidationLimits.AI_PUNISHMENT_DESCRIPTION_MAX_LENGTH)
            String aiDescription,
            Boolean enabled
    ) {
        public AIModerationSettings.AIPunishmentConfig toConfig() {
            return AIModerationSettings.AIPunishmentConfig.builder()
                    .id(id)
                    .name(name)
                    .aiDescription(aiDescription)
                    .enabled(Boolean.TRUE.equals(enabled))
                    .build();
        }
    }
}
