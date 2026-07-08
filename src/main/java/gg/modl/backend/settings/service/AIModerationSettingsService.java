package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIModerationSettingsService {
    private final SettingsDocumentService settingsDocumentService;
    private final ObjectMapper objectMapper;
    private static final String SETTINGS_TYPE_AI_MODERATION = "aiModerationSettings";

    public AIModerationSettings updateAIModerationSettings(Server server, AIModerationSettings newSettings) {
        SettingsDocumentService.RawSettingsState current = settingsDocumentService.getRawState(server, SETTINGS_TYPE_AI_MODERATION);

        AIModerationSettings toPersist = AIModerationSettings.builder()
            .enableAIReview(newSettings.isEnableAIReview())
            .enableAutomatedActions(newSettings.isEnableAutomatedActions())
            .aiPunishmentConfigs(newSettings.getAiPunishmentConfigs() != null
                ? newSettings.getAiPunishmentConfigs()
                : new HashMap<>())
            .build();

        SettingsDocumentService.RawSettingsState saved =
            settingsDocumentService.saveRawState(server, SETTINGS_TYPE_AI_MODERATION, current.version(), codec().encode(toPersist));
        return codec().decode(saved.data());
    }

    public AIModerationSettings getAIModerationSettings(Server server) {
        return codec().decode(settingsDocumentService.getRawState(server, SETTINGS_TYPE_AI_MODERATION).data());
    }

    private SettingsCodec<AIModerationSettings> codec() {
        return SettingsCodec.of(objectMapper, AIModerationSettings.class, this::createDefaultSettings);
    }

    private AIModerationSettings createDefaultSettings() {
        Map<String, AIModerationSettings.AIPunishmentConfig> defaultConfigs = new HashMap<>();
        defaultConfigs.put("6", AIModerationSettings.AIPunishmentConfig.builder()
            .id("6")
            .name("Chat Abuse")
            .aiDescription(
                "Chat abuse is the act of spamming, excessive profanity, abusive language, inappropriate topics or jokes, and misleading information")
            .enabled(true)
            .build());
        defaultConfigs.put("7", AIModerationSettings.AIPunishmentConfig.builder()
            .id("7")
            .name("Anti Social")
            .aiDescription(
                "Anti social is the act of harassing, threatening, black-mailing, or otherwise abusing another player or group of players. This includes bigotry and other forms of discrimination against protected classes.")
            .enabled(true)
            .build());

        return AIModerationSettings.builder()
            .enableAIReview(false)
            .enableAutomatedActions(false)
            .aiPunishmentConfigs(defaultConfigs)
            .build();
    }
}
