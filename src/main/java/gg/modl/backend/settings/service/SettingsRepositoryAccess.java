package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettingsRepositoryAccess {
    private final SettingsMongoRepository settingsRepository;

    public Optional<Settings> findSettings(Server server, String type) {
        return settingsRepository.findByType(server, type);
    }

    public void upsertSettings(Server server, String type, Map<String, Object> data) {
        settingsRepository.upsertData(server, type, data);
    }

    public void upsertListSettings(Server server, String type, Object data) {
        settingsRepository.upsertListData(server, type, data);
    }

    public void updateDataSettings(Server server, String type, Map<String, Object> data) {
        settingsRepository.updateDataByType(server, type, data);
    }

    public void removeSettings(Server server, String type) {
        settingsRepository.removeByType(server, type);
    }

    public void saveEntity(Server server, Settings settings) {
        settingsRepository.saveEntity(server, settings);
    }
}
