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
    private static final String SETTINGS_TYPE_OFFENDER_THRESHOLDS = "offenderThresholds";

    private final DynamicMongoTemplateProvider mongoProvider;

    public OffenderThresholdSettings getThresholdSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_OFFENDER_THRESHOLDS));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return OffenderThresholdSettings.defaults();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        return OffenderThresholdSettings.builder()
                .mediumThreshold(getIntValue(data, "mediumThreshold", 1))
                .habitualThreshold(getIntValue(data, "habitualThreshold", 3))
                .build();
    }

    public OffenderThresholdSettings updateThresholdSettings(Server server, OffenderThresholdSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_OFFENDER_THRESHOLDS));

        Map<String, Object> data = Map.of(
                "mediumThreshold", newSettings.getMediumThreshold(),
                "habitualThreshold", newSettings.getHabitualThreshold()
        );

        Update update = new Update()
                .set("type", SETTINGS_TYPE_OFFENDER_THRESHOLDS)
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
