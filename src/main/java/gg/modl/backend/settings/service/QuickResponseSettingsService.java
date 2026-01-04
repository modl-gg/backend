package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuickResponseSettingsService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final ObjectMapper objectMapper;

    public QuickResponseSettings getQuickResponseSettings(Server server) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("type").is("quickResponses"));
        Settings settings = template.findOne(query, Settings.class, CollectionName.SETTINGS);

        if (settings == null || settings.getData() == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(settings.getData(), QuickResponseSettings.class);
        } catch (Exception e) {
            log.error("Failed to parse quick response settings", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public QuickResponseSettings.Action findAction(QuickResponseSettings settings, String categoryId, String actionId) {
        if (settings == null || settings.getCategories() == null) {
            return null;
        }

        for (QuickResponseSettings.Category category : settings.getCategories()) {
            if (category.getId().equals(categoryId) && category.getActions() != null) {
                for (QuickResponseSettings.Action action : category.getActions()) {
                    if (action.getId().equals(actionId)) {
                        return action;
                    }
                }
            }
        }

        return null;
    }
}
