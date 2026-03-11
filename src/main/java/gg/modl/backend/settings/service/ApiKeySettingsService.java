package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import gg.modl.backend.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ApiKeySettingsService extends AbstractSettingsService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";
    private static final String API_KEY_FIELD = "api_key";

    private final IdGenerator idGenerator;
    private final ServerMongoRepository serverRepository;

    public ApiKeySettingsService(SettingsMongoRepository settingsRepository, IdGenerator idGenerator,
                                 ServerMongoRepository serverRepository) {
        super(settingsRepository);
        this.idGenerator = idGenerator;
        this.serverRepository = serverRepository;
    }

    @Nullable
    public String getApiKeyFromSettings(@NotNull Server server) {
        Settings settings = findSettings(server, SETTINGS_TYPE_API_KEYS).orElse(null);
        if (settings == null || settings.getData() == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        Object apiKey = data.get(API_KEY_FIELD);
        return apiKey instanceof String ? (String) apiKey : null;
    }

    public void syncApiKeyToServer(@NotNull Server server, @NotNull String apiKey) {
        serverRepository.updateApiKey(server.getId(), apiKey);
    }

    @Nullable
    public Server findServerByApiKey(@NotNull String apiKey) {
        Server server = serverRepository.findByApiKey(apiKey).orElse(null);
        if (server != null) {
            return server;
        }

        return findServerByApiKeyInSettings(apiKey);
    }

    @Nullable
    private Server findServerByApiKeyInSettings(@NotNull String apiKey) {
        List<Server> servers = serverRepository.findAll();
        for (Server server : servers) {
            if (server.getDatabaseName() == null) {
                continue;
            }

            String settingsApiKey = getApiKeyFromSettings(server);
            if (apiKey.equals(settingsApiKey)) {
                syncApiKeyToServer(server, settingsApiKey);
                server.setApiKey(apiKey);
                return server;
            }
        }

        return null;
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
