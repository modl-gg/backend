package gg.modl.backend.settings.service;

import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.infrastructure.util.MongoKeyUtils;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
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

    public void upsertListSettings(Server server, String type, Object data) {
        settingsRepository.upsertListData(server, type, MongoKeyUtils.sanitizeValue(data));
    }
}
