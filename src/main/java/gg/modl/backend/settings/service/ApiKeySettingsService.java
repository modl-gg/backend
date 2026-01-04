package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeySettingsService {
    private static final String SETTINGS_TYPE_API_KEYS = "apiKeys";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DynamicMongoTemplateProvider mongoProvider;

    public String generateApiKey(Server server, String keyType) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_API_KEYS));

        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = settings != null && settings.getData() != null
                ? new HashMap<>((Map<String, Object>) settings.getData())
                : new HashMap<>();

        String newApiKey = generateSecureApiKey();
        String fieldName = getFieldNameForType(keyType);

        data.put(fieldName, newApiKey);

        Update update = new Update()
                .set("type", SETTINGS_TYPE_API_KEYS)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return newApiKey;
    }

    public String revealApiKey(Server server, String keyType) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_API_KEYS));

        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

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
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_API_KEYS));

        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

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

        Update update = new Update()
                .set("type", SETTINGS_TYPE_API_KEYS)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return true;
    }

    public boolean hasApiKey(Server server, String keyType) {
        String apiKey = revealApiKey(server, keyType);
        return apiKey != null && !apiKey.isBlank();
    }

    private String generateSecureApiKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "modl_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String getFieldNameForType(String keyType) {
        return switch (keyType.toLowerCase()) {
            case "ticket" -> "ticket_api_key";
            case "minecraft" -> "minecraft_api_key";
            default -> "api_key";
        };
    }
}
