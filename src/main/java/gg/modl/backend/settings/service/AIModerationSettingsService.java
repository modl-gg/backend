package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AIModerationSettingsService extends AbstractSettingsService {
    private static final String SETTINGS_TYPE_AI_MODERATION = "aiModerationSettings";

    private final ObjectMapper objectMapper;

    public AIModerationSettingsService(SettingsMongoRepository settingsRepository, ObjectMapper objectMapper) {
        super(settingsRepository);
        this.objectMapper = objectMapper;
    }

    public AIModerationSettings getAIModerationSettings(Server server) {
        Settings settings = findSettings(server, SETTINGS_TYPE_AI_MODERATION).orElse(null);

        if (settings == null || settings.getData() == null) {
            AIModerationSettings defaults = createDefaultSettings();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.convertValue(defaults, Map.class);
                settingsRepository.saveEntity(server, new Settings(null, SETTINGS_TYPE_AI_MODERATION, data));
            } catch (Exception e) {
                log.warn("Failed to create default AI moderation settings for server {}: {}", server.getDatabaseName(), e.getMessage());
            }
            return defaults;
        }

        try {
            return objectMapper.convertValue(settings.getData(), AIModerationSettings.class);
        } catch (Exception e) {
            log.error("Error converting AI moderation settings", e);
            return AIModerationSettings.builder()
                    .enableAIReview(false)
                    .enableAutomatedActions(false)
                    .strictnessLevel("STANDARD")
                    .aiPunishmentConfigs(new HashMap<>())
                    .build();
        }
    }

    public AIModerationSettings updateAIModerationSettings(Server server, AIModerationSettings newSettings) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.convertValue(newSettings, Map.class);
        upsertSettings(server, SETTINGS_TYPE_AI_MODERATION, data);
        return getAIModerationSettings(server);
    }

    private AIModerationSettings createDefaultSettings() {
        Map<String, AIModerationSettings.AIPunishmentConfig> defaultConfigs = new HashMap<>();
        defaultConfigs.put("6", AIModerationSettings.AIPunishmentConfig.builder()
                .id("6")
                .name("Chat Abuse")
                .aiDescription("Chat abuse is the act of spamming, excessive profanity, abusive language, inappropriate topics or jokes, and misleading information")
                .enabled(true)
                .build());
        defaultConfigs.put("7", AIModerationSettings.AIPunishmentConfig.builder()
                .id("7")
                .name("Anti Social")
                .aiDescription("Anti social is the act of harassing, threatening, black-mailing, or otherwise abusing another player or group of players. This includes bigotry and other forms of discrimination against protected classes.")
                .enabled(true)
                .build());

        return AIModerationSettings.builder()
                .enableAIReview(false)
                .enableAutomatedActions(false)
                .strictnessLevel("STANDARD")
                .aiPunishmentConfigs(defaultConfigs)
                .build();
    }
}
