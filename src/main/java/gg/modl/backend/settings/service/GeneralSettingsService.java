package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.SupportedLanguages;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeneralSettingsService {
    private final SettingsDocumentService settingsDocumentService;
    private final ObjectMapper objectMapper;
    private static final String SETTINGS_TYPE_GENERAL = "general";
    private static final int MAX_SERVER_NAME_LENGTH = 80;
    private static final int MAX_URL_LENGTH = 2048;

    public GeneralSettings getGeneralSettings(Server server) {
        return getGeneralSettingsState(server).data();
    }

    public VersionedSettings<GeneralSettings> getGeneralSettingsState(Server server) {
        SettingsDocumentService.RawSettingsState state = settingsDocumentService.getRawState(server, SETTINGS_TYPE_GENERAL);
        GeneralSettings settings = mapToGeneralSettings(state.data());
        return new VersionedSettings<>(settings, state.version(), state.updatedAt());
    }

    private GeneralSettings mapToGeneralSettings(Map<String, Object> data) {
        GeneralSettings mapped = codec().decode(data);
        return GeneralSettings.builder()
            .serverDisplayName(sanitizeOrEmpty(mapped.getServerDisplayName(), MAX_SERVER_NAME_LENGTH))
            .discordWebhookUrl(sanitizeOrEmpty(mapped.getDiscordWebhookUrl(), MAX_URL_LENGTH))
            .homepageIconUrl(sanitizeOrEmpty(mapped.getHomepageIconUrl(), MAX_URL_LENGTH))
            .panelIconUrl(sanitizeOrEmpty(mapped.getPanelIconUrl(), MAX_URL_LENGTH))
            .defaultLanguage(resolveLanguage(mapped.getDefaultLanguage()))
            .build();
    }

    private String resolveLanguage(String value) {
        return SupportedLanguages.isSupported(value) ? value : SupportedLanguages.DEFAULT;
    }

    private SettingsCodec<GeneralSettings> codec() {
        return SettingsCodec.of(objectMapper, GeneralSettings.class, this::defaultGeneralSettings);
    }

    private String sanitizeOrEmpty(String value, int maxLength) {
        String sanitized = sanitize(value, maxLength);
        return sanitized != null ? sanitized : "";
    }

    private GeneralSettings defaultGeneralSettings() {
        return GeneralSettings.builder()
            .serverDisplayName("")
            .discordWebhookUrl("")
            .homepageIconUrl("")
            .panelIconUrl("")
            .defaultLanguage(SupportedLanguages.DEFAULT)
            .build();
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    public VersionedSettings<GeneralSettings> patchGeneralSettings(
        Server server,
        long expectedVersion,
        GeneralSettings patch
    ) {
        SettingsDocumentService.RawSettingsState current = settingsDocumentService.getRawState(server, SETTINGS_TYPE_GENERAL);
        Map<String, Object> data = new LinkedHashMap<>(current.data());

        putIfNotNull(data, "serverDisplayName", patch.getServerDisplayName(), MAX_SERVER_NAME_LENGTH);
        putIfNotNull(data, "discordWebhookUrl", patch.getDiscordWebhookUrl(), MAX_URL_LENGTH);
        putIfNotNull(data, "homepageIconUrl", patch.getHomepageIconUrl(), MAX_URL_LENGTH);
        putIfNotNull(data, "panelIconUrl", patch.getPanelIconUrl(), MAX_URL_LENGTH);
        putLanguageIfNotNull(data, patch.getDefaultLanguage());

        SettingsDocumentService.RawSettingsState updated = settingsDocumentService.saveRawState(
            server,
            SETTINGS_TYPE_GENERAL,
            expectedVersion,
            data
        );
        return new VersionedSettings<>(mapToGeneralSettings(updated.data()), updated.version(), updated.updatedAt());
    }

    private void putIfNotNull(Map<String, Object> data, String key, String value, int maxLength) {
        if (value != null) {
            data.put(key, sanitize(value, maxLength));
        }
    }

    private void putLanguageIfNotNull(Map<String, Object> data, String value) {
        if (value == null) {
            return;
        }
        String language = value.trim();
        if (!SupportedLanguages.isSupported(language)) {
            throw new ValidationException("Unsupported default language: " + language);
        }
        data.put("defaultLanguage", language);
    }
}
