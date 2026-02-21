package gg.modl.backend.settings.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.GeneralSettings;
import gg.modl.backend.settings.data.Label;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
                    .labels(new ArrayList<>())
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();

        return GeneralSettings.builder()
                .serverDisplayName(getStringValue(data, "serverDisplayName"))
                .discordWebhookUrl(getStringValue(data, "discordWebhookUrl"))
                .homepageIconUrl(getStringValue(data, "homepageIconUrl"))
                .panelIconUrl(getStringValue(data, "panelIconUrl"))
                .labels(getLabelsValue(data, "labels"))
                .build();
    }

    public GeneralSettings updateGeneralSettings(Server server, GeneralSettings newSettings) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = new Query(Criteria.where("type").is(SETTINGS_TYPE_GENERAL));

        Map<String, Object> data = new HashMap<>();
        data.put("serverDisplayName", newSettings.getServerDisplayName() != null ? newSettings.getServerDisplayName() : "");
        data.put("discordWebhookUrl", newSettings.getDiscordWebhookUrl() != null ? newSettings.getDiscordWebhookUrl() : "");
        data.put("homepageIconUrl", newSettings.getHomepageIconUrl() != null ? newSettings.getHomepageIconUrl() : "");
        data.put("panelIconUrl", newSettings.getPanelIconUrl() != null ? newSettings.getPanelIconUrl() : "");
        data.put(
                "labels",
                newSettings.getLabels() != null
                        ? newSettings.getLabels().stream().map(this::labelToMap).collect(Collectors.toList())
                        : new ArrayList<>()
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

    @SuppressWarnings("unchecked")
    private List<Label> getLabelsValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            List<Map<String, Object>> labelMaps = (List<Map<String, Object>>) value;
            return labelMaps.stream()
                    .map(this::mapToLabel)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Label mapToLabel(Map<String, Object> map) {
        return Label.builder()
                .id(map.get("id") != null ? map.get("id").toString() : UUID.randomUUID().toString())
                .name(map.get("name") != null ? map.get("name").toString() : "")
                .color(map.get("color") != null ? map.get("color").toString() : "#6b7280")
                .description(map.get("description") != null ? map.get("description").toString() : null)
                .build();
    }

    private Map<String, Object> labelToMap(Label label) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", label.getId() != null ? label.getId() : UUID.randomUUID().toString());
        map.put("name", label.getName());
        map.put("color", label.getColor());
        if (label.getDescription() != null) {
            map.put("description", label.getDescription());
        }
        return map;
    }
}
