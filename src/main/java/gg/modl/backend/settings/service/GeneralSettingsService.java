package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.SupportedLanguages;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GeneralSettingsService {
    private static final String SETTINGS_TYPE_GENERAL = "general";
    private static final int MAX_SERVER_NAME_LENGTH = 80;
    private static final int MAX_URL_LENGTH = 2048;

    private final ObjectMapper objectMapper;
    private final VersionedSettingsSupport<GeneralSettings> support;

    public GeneralSettingsService(SettingsDocumentService settingsDocumentService, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.support = VersionedSettingsSupport.of(
            settingsDocumentService, SETTINGS_TYPE_GENERAL, this::mapToGeneralSettings);
    }

    public GeneralSettings getGeneralSettings(Server server) {
        return support.get(server);
    }

    public VersionedSettings<GeneralSettings> getGeneralSettingsState(Server server) {
        return support.state(server);
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
        Map<String, Object> data = support.currentData(server);

        putIfNotNull(data, "serverDisplayName", patch.getServerDisplayName(), MAX_SERVER_NAME_LENGTH);
        putIfNotNull(data, "discordWebhookUrl", patch.getDiscordWebhookUrl(), MAX_URL_LENGTH);
        putIfNotNull(data, "homepageIconUrl", patch.getHomepageIconUrl(), MAX_URL_LENGTH);
        putIfNotNull(data, "panelIconUrl", patch.getPanelIconUrl(), MAX_URL_LENGTH);
        putLanguageIfNotNull(data, patch.getDefaultLanguage());

        return support.save(server, expectedVersion, data);
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
