package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
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
public class GeneralSettingsService {
    private static final String SETTINGS_TYPE_GENERAL = "general";

    private final DynamicMongoTemplateProvider mongoProvider;

    public GeneralSettings getGeneralSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_GENERAL));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return GeneralSettings.builder()
                    .serverDisplayName("")
                    .discordWebhookUrl("")
                    .homepageIconUrl("")
                    .panelIconUrl("")
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();
        return GeneralSettings.builder()
                .serverDisplayName(getStringValue(data, "serverDisplayName"))
                .discordWebhookUrl(getStringValue(data, "discordWebhookUrl"))
                .homepageIconUrl(getStringValue(data, "homepageIconUrl"))
                .panelIconUrl(getStringValue(data, "panelIconUrl"))
                .build();
    }

    public GeneralSettings updateGeneralSettings(Server server, GeneralSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_GENERAL));

        Map<String, Object> data = Map.of(
                "serverDisplayName", newSettings.getServerDisplayName() != null ? newSettings.getServerDisplayName() : "",
                "discordWebhookUrl", newSettings.getDiscordWebhookUrl() != null ? newSettings.getDiscordWebhookUrl() : "",
                "homepageIconUrl", newSettings.getHomepageIconUrl() != null ? newSettings.getHomepageIconUrl() : "",
                "panelIconUrl", newSettings.getPanelIconUrl() != null ? newSettings.getPanelIconUrl() : ""
        );

        Update update = new Update()
                .set("type", SETTINGS_TYPE_GENERAL)
                .set("data", data);

        template.upsert(query, update, Settings.class, CollectionName.SETTINGS);

        return getGeneralSettings(server);
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : "";
    }
}
