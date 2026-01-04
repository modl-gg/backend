package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.AIModerationSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIModerationSettingsService {
    private static final String SETTINGS_TYPE_AI_MODERATION = "aiModerationSettings";

    private final DynamicMongoTemplateProvider mongoProvider;
    private final ObjectMapper objectMapper;

    public AIModerationSettings getAIModerationSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_AI_MODERATION));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return AIModerationSettings.builder()
                    .enableAIReview(false)
                    .enableAutomatedActions(false)
                    .strictnessLevel("standard")
                    .aiPunishmentConfigs(new HashMap<>())
                    .build();
        }

        try {
            return objectMapper.convertValue(settings.getData(), AIModerationSettings.class);
        } catch (Exception e) {
            log.error("Error converting AI moderation settings", e);
            return AIModerationSettings.builder()
                    .enableAIReview(false)
                    .enableAutomatedActions(false)
                    .strictnessLevel("standard")
                    .aiPunishmentConfigs(new HashMap<>())
                    .build();
        }
    }

    public AIModerationSettings updateAIModerationSettings(Server server, AIModerationSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_AI_MODERATION));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = objectMapper.convertValue(newSettings, Map.class);

        Update update = new Update()
                .set("type", SETTINGS_TYPE_AI_MODERATION)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return getAIModerationSettings(server);
    }
}
