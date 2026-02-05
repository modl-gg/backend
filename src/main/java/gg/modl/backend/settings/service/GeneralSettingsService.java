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

import java.util.*;
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
                    .bugReportTags(new ArrayList<>())
                    .playerReportTags(new ArrayList<>())
                    .appealTags(new ArrayList<>())
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) settings.getData();

        // Get labels (new system)
        List<Label> labels = getLabelsValue(data, "labels");

        // Get deprecated tags for backwards compatibility
        List<String> bugReportTags = getStringListValue(data, "bugReportTags");
        List<String> playerReportTags = getStringListValue(data, "playerReportTags");
        List<String> appealTags = getStringListValue(data, "appealTags");

        // Migration: if labels is empty but tags exist, migrate them
        if (labels.isEmpty() && (!bugReportTags.isEmpty() || !playerReportTags.isEmpty() || !appealTags.isEmpty())) {
            labels = migrateTagsToLabels(bugReportTags, playerReportTags, appealTags);
            // Save migrated labels
            data.put("labels", labels.stream().map(this::labelToMap).collect(Collectors.toList()));
            Update update = new Update().set("data", data);
            template.updateFirst(query, update, Settings.class, CollectionName.SETTINGS);
        }

        return GeneralSettings.builder()
                .serverDisplayName(getStringValue(data, "serverDisplayName"))
                .discordWebhookUrl(getStringValue(data, "discordWebhookUrl"))
                .homepageIconUrl(getStringValue(data, "homepageIconUrl"))
                .panelIconUrl(getStringValue(data, "panelIconUrl"))
                .labels(labels)
                .bugReportTags(bugReportTags)
                .playerReportTags(playerReportTags)
                .appealTags(appealTags)
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

        // New labels system
        if (newSettings.getLabels() != null) {
            data.put("labels", newSettings.getLabels().stream().map(this::labelToMap).collect(Collectors.toList()));
        } else {
            data.put("labels", new ArrayList<>());
        }

        // Deprecated: kept for backwards compatibility
        data.put("bugReportTags", newSettings.getBugReportTags() != null ? newSettings.getBugReportTags() : new ArrayList<>());
        data.put("playerReportTags", newSettings.getPlayerReportTags() != null ? newSettings.getPlayerReportTags() : new ArrayList<>());
        data.put("appealTags", newSettings.getAppealTags() != null ? newSettings.getAppealTags() : new ArrayList<>());

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
    private List<String> getStringListValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return new ArrayList<>();
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

    /**
     * Migrates legacy tags to the new unified label system with default colors.
     */
    private List<Label> migrateTagsToLabels(List<String> bugTags, List<String> playerTags, List<String> appealTags) {
        List<Label> labels = new ArrayList<>();
        Set<String> addedNames = new HashSet<>();

        // Default colors for different tag types
        String bugColor = "#d73a4a";      // Red
        String playerColor = "#0969da";    // Blue
        String appealColor = "#8250df";    // Purple

        for (String tag : bugTags) {
            if (!addedNames.contains(tag.toLowerCase())) {
                labels.add(Label.builder()
                        .id(UUID.randomUUID().toString())
                        .name(tag)
                        .color(bugColor)
                        .description("Migrated from bug report tags")
                        .build());
                addedNames.add(tag.toLowerCase());
            }
        }

        for (String tag : playerTags) {
            if (!addedNames.contains(tag.toLowerCase())) {
                labels.add(Label.builder()
                        .id(UUID.randomUUID().toString())
                        .name(tag)
                        .color(playerColor)
                        .description("Migrated from player report tags")
                        .build());
                addedNames.add(tag.toLowerCase());
            }
        }

        for (String tag : appealTags) {
            if (!addedNames.contains(tag.toLowerCase())) {
                labels.add(Label.builder()
                        .id(UUID.randomUUID().toString())
                        .name(tag)
                        .color(appealColor)
                        .description("Migrated from appeal tags")
                        .build());
                addedNames.add(tag.toLowerCase());
            }
        }

        return labels;
    }
}
