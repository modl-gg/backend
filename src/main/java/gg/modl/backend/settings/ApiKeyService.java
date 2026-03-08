package gg.modl.backend.settings;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";
    private static final String API_KEY_FIELD = "api_key";

    private final SettingsMongoRepository settingsRepository;
    private final ServerMongoRepository serverRepository;

    @Nullable
    public String getApiKeyFromSettings(@NotNull Server server) {
        Settings settings = settingsRepository.findByType(server, SETTINGS_TYPE_API_KEYS).orElse(null);
        if (settings == null || settings.getData() == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) settings.getData();
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
}
