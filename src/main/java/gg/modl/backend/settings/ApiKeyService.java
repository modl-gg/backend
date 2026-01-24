package gg.modl.backend.settings;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.ServerField;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";
    private static final String API_KEY_FIELD = "api_key";

    private final DynamicMongoTemplateProvider mongoProvider;

    @Nullable
    public String getApiKeyFromSettings(@NotNull Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_API_KEYS));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) settings.getData();
        Object apiKey = data.get(API_KEY_FIELD);
        return apiKey instanceof String ? (String) apiKey : null;
    }

    public void syncApiKeyToServer(@NotNull Server server) {
        String apiKey = getApiKeyFromSettings(server);
        if (apiKey == null) {
            return;
        }

        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where("_id").is(server.getId()));
        Update update = new Update().set(ServerField.API_KEY, apiKey);
        globalDb.updateFirst(query, update, Server.class, CollectionName.MODL_SERVERS);
    }

    @Nullable
    public Server findServerByApiKey(@NotNull String apiKey) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query query = new Query(Criteria.where(ServerField.API_KEY).is(apiKey));
        Server server = globalDb.findOne(query, Server.class, CollectionName.MODL_SERVERS);

        if (server != null) {
            return server;
        }

        return findServerByApiKeyInSettings(apiKey);
    }

    @Nullable
    private Server findServerByApiKeyInSettings(@NotNull String apiKey) {
        MongoTemplate globalDb = mongoProvider.getGlobalDatabase();
        Query allServersQuery = new Query();
        List<Server> servers = globalDb.find(allServersQuery, Server.class, CollectionName.MODL_SERVERS);

        for (Server server : servers) {
            if (server.getDatabaseName() == null) {
                continue;
            }

            String settingsApiKey = getApiKeyFromSettings(server);
            if (apiKey.equals(settingsApiKey)) {
                syncApiKeyToServer(server);
                server.setApiKey(apiKey);
                return server;
            }
        }

        return null;
    }
}
