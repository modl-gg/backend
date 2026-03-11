package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractSettingsService {
    protected final SettingsMongoRepository settingsRepository;

    protected Optional<Settings> findSettings(Server server, String type) {
        return settingsRepository.findByType(server, type);
    }

    protected void upsertSettings(Server server, String type, Map<String, Object> data) {
        settingsRepository.upsertData(server, type, data);
    }

    protected void upsertListSettings(Server server, String type, Object data) {
        settingsRepository.upsertListData(server, type, data);
    }

    protected void updateDataSettings(Server server, String type, Map<String, Object> data) {
        settingsRepository.updateDataByType(server, type, data);
    }

    protected void removeSettings(Server server, String type) {
        settingsRepository.removeByType(server, type);
    }
}
