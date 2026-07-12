package gg.modl.backend.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.QuickResponseSettings;
import gg.modl.backend.settings.dto.request.UpdateQuickResponsesRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuickResponseSettingsService {
    private static final String SETTINGS_TYPE_QUICK_RESPONSES = "quickResponses";

    private final ObjectMapper objectMapper;
    private final VersionedSettingsSupport<QuickResponseSettings> support;

    public QuickResponseSettingsService(SettingsDocumentService settingsDocumentService, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.support = VersionedSettingsSupport.of(
            settingsDocumentService, SETTINGS_TYPE_QUICK_RESPONSES, this::mapToQuickResponseSettings);
    }

    public QuickResponseSettings getQuickResponseSettings(Server server) {
        return support.get(server);
    }

    public VersionedSettings<QuickResponseSettings> getQuickResponseSettingsState(Server server) {
        return support.state(server);
    }

    public QuickResponseSettings.Action findAction(QuickResponseSettings settings, String categoryId, String actionId) {
        if (settings == null || settings.getCategories() == null || categoryId == null || actionId == null) {
            return null;
        }

        for (QuickResponseSettings.Category category : settings.getCategories()) {
            if (category == null || category.getId() == null || category.getActions() == null) {
                continue;
            }
            if (categoryId.equals(category.getId())) {
                for (QuickResponseSettings.Action action : category.getActions()) {
                    if (action != null && actionId.equals(action.getId())) {
                        return action;
                    }
                }
            }
        }

        return null;
    }

    public VersionedSettings<QuickResponseSettings> patchQuickResponseSettings(
        Server server,
        long expectedVersion,
        UpdateQuickResponsesRequest quickResponses
    ) {
        QuickResponseSettings currentSettings = support.get(server);
        if (quickResponses != null && quickResponses.categories() != null) {
            currentSettings.setCategories(quickResponses.categories());
        }

        Map<String, Object> data = codec().encode(currentSettings);
        return support.save(server, expectedVersion, new LinkedHashMap<>(data));
    }

    private QuickResponseSettings mapToQuickResponseSettings(Map<String, Object> data) {
        QuickResponseSettings mapped = codec().decode(data);
        if (mapped.getCategories() == null) {
            mapped.setCategories(new ArrayList<>());
        }
        return mapped;
    }

    private SettingsCodec<QuickResponseSettings> codec() {
        return SettingsCodec.of(objectMapper, QuickResponseSettings.class, this::defaultQuickResponseSettings);
    }

    private QuickResponseSettings defaultQuickResponseSettings() {
        QuickResponseSettings settings = new QuickResponseSettings();
        settings.setCategories(new ArrayList<>());
        return settings;
    }
}
