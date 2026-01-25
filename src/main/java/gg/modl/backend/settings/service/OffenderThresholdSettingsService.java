package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OffenderThresholdSettingsService {
    private static final String SETTINGS_TYPE_STATUS_THRESHOLDS = "statusThresholds";

    private final DynamicMongoTemplateProvider mongoProvider;

    public OffenderThresholdSettings getThresholdSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_STATUS_THRESHOLDS));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return OffenderThresholdSettings.defaults();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) settings.getData();

            OffenderThresholdSettings.CategoryThresholds social = parseThresholds(data, "social", 4, 8);
            OffenderThresholdSettings.CategoryThresholds gameplay = parseThresholds(data, "gameplay", 5, 10);

            return OffenderThresholdSettings.builder()
                    .social(social)
                    .gameplay(gameplay)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse status thresholds, using defaults: {}", e.getMessage());
            return OffenderThresholdSettings.defaults();
        }
    }

    @SuppressWarnings("unchecked")
    private OffenderThresholdSettings.CategoryThresholds parseThresholds(
            Map<String, Object> data, String category, int defaultMedium, int defaultHabitual) {
        Object categoryData = data.get(category);
        if (categoryData instanceof Map) {
            Map<String, Object> thresholds = (Map<String, Object>) categoryData;
            int medium = getIntValue(thresholds, "medium", defaultMedium);
            int habitual = getIntValue(thresholds, "habitual", defaultHabitual);
            return new OffenderThresholdSettings.CategoryThresholds(medium, habitual);
        }
        return new OffenderThresholdSettings.CategoryThresholds(defaultMedium, defaultHabitual);
    }

    public OffenderThresholdSettings updateThresholdSettings(Server server, OffenderThresholdSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_STATUS_THRESHOLDS));

        Map<String, Object> data = Map.of(
                "social", Map.of(
                        "medium", newSettings.getSocial().getMedium(),
                        "habitual", newSettings.getSocial().getHabitual()
                ),
                "gameplay", Map.of(
                        "medium", newSettings.getGameplay().getMedium(),
                        "habitual", newSettings.getGameplay().getHabitual()
                )
        );

        Update update = new Update()
                .set("type", SETTINGS_TYPE_STATUS_THRESHOLDS)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return getThresholdSettings(server);
    }

    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
