package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import gg.modl.backend.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ApiKeySettingsService extends AbstractSettingsService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";

    private final IdGenerator idGenerator;

    public ApiKeySettingsService(SettingsMongoRepository settingsRepository, IdGenerator idGenerator) {
        super(settingsRepository);
        this.idGenerator = idGenerator;
    }

    public String generateApiKey(Server server, String keyType) {
        Settings settings = findSettings(server, SETTINGS_TYPE_API_KEYS).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = settings != null && settings.getData() != null
                ? new HashMap<>((Map<String, Object>) settings.getData())
                : new HashMap<>();

        String newApiKey = generateSecureApiKey();
        String fieldName = getFieldNameForType(keyType);

        data.put(fieldName, newApiKey);
        upsertSettings(server, SETTINGS_TYPE_API_KEYS, data);

        return newApiKey;
    }

    public String revealApiKey(Server server, String keyType) {
        Settings settings = findSettings(server, SETTINGS_TYPE_API_KEYS).orElse(null);

        if (settings == null || settings.getData() == null) {
            return null;
        }

        String fieldName = getFieldNameForType(keyType);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        Object apiKey = data.get(fieldName);
        return apiKey instanceof String ? (String) apiKey : null;
    }

    public boolean deleteApiKey(Server server, String keyType) {
        Settings settings = findSettings(server, SETTINGS_TYPE_API_KEYS).orElse(null);

        if (settings == null || settings.getData() == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = new HashMap<>((Map<String, Object>) settings.getData());
        String fieldName = getFieldNameForType(keyType);

        if (!data.containsKey(fieldName)) {
            return false;
        }

        data.remove(fieldName);
        upsertSettings(server, SETTINGS_TYPE_API_KEYS, data);

        return true;
    }

    public boolean hasApiKey(Server server, String keyType) {
        String apiKey = revealApiKey(server, keyType);
        return apiKey != null && !apiKey.isBlank();
    }

    private String generateSecureApiKey() {
        return "modl_" + idGenerator.generateToken();
    }

    private String getFieldNameForType(String keyType) {
        return switch (keyType.toLowerCase()) {
            case "ticket" -> "ticket_api_key";
            case "minecraft" -> "minecraft_api_key";
            default -> "api_key";
        };
    }
}
