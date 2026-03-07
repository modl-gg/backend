package gg.modl.backend.settings.service;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.fields.SettingsFields;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsDocumentService {
    private static final long INITIAL_VERSION = 0L;

    private final SettingsMongoRepository settingsRepository;

    public RawSettingsState getRawState(Server server, String type) {
        Settings settings = findLatestSettingsDocument(server, type);
        return toRawState(settings);
    }

    public RawSettingsState saveRawState(Server server, String type, long expectedVersion, Map<String, Object> data) {
        Settings current = findLatestSettingsDocument(server, type);
        RawSettingsState currentState = toRawState(current);

        if (currentState.version() != expectedVersion) {
            throw new SettingsConflictException(
                    "Settings were modified by another user. Reload and retry.",
                    currentState.version()
            );
        }

        Map<String, Object> normalizedData = data == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(data);
        Date now = new Date();

        if (!currentState.exists()) {
            try {
                Settings inserted = new Settings(null, type, normalizedData, expectedVersion + 1, now);
                settingsRepository.saveEntity(server, inserted);
                return new RawSettingsState(normalizedData, expectedVersion + 1, now, true);
            } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
                RawSettingsState latest = getRawState(server, type);
                throw new SettingsConflictException(
                        "Settings were modified by another user. Reload and retry.",
                        latest.version()
                );
            }
        }

        if (currentState.data().equals(normalizedData)) {
            return currentState;
        }

        Query updateQuery = Query.query(MongoQueries.where(SettingsFields.ID).is(current.getId())
                .andOperator(versionCriteria(expectedVersion)));
        Update update = new Update();
        MongoUpdates.set(update, SettingsFields.TYPE, type);
        MongoUpdates.set(update, SettingsFields.DATA, normalizedData);
        MongoUpdates.set(update, SettingsFields.VERSION, expectedVersion + 1);
        MongoUpdates.set(update, SettingsFields.UPDATED_AT, now);

        UpdateResult result = settingsRepository.updateFirst(server, updateQuery, update);
        if (result.getModifiedCount() == 0) {
            RawSettingsState latest = getRawState(server, type);
            throw new SettingsConflictException(
                    "Settings were modified by another user. Reload and retry.",
                    latest.version()
            );
        }

        return new RawSettingsState(normalizedData, expectedVersion + 1, now, true);
    }

    private Settings findLatestSettingsDocument(Server server, String type) {
        Query query = Query.query(MongoQueries.where(SettingsFields.TYPE).is(type))
                .with(Sort.by(
                        Sort.Order.desc(SettingsFields.VERSION.path()),
                        Sort.Order.desc(SettingsFields.UPDATED_AT.path()),
                        Sort.Order.desc(SettingsFields.ID.path())
                ))
                .limit(2);
        List<Settings> matches = settingsRepository.find(server, query);
        if (matches.size() > 1) {
            log.warn(
                    "Detected duplicate settings documents for type '{}'. Using latest id '{}'.",
                    type,
                    matches.get(0).getId()
            );
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private Criteria versionCriteria(long expectedVersion) {
        if (expectedVersion == INITIAL_VERSION) {
            return new Criteria().orOperator(
                    MongoQueries.where(SettingsFields.VERSION).is(INITIAL_VERSION),
                    MongoQueries.where(SettingsFields.VERSION).exists(false),
                    MongoQueries.where(SettingsFields.VERSION).is(null)
            );
        }
        return MongoQueries.where(SettingsFields.VERSION).is(expectedVersion);
    }

    @SuppressWarnings("unchecked")
    private RawSettingsState toRawState(Settings settings) {
        if (settings == null) {
            return new RawSettingsState(new LinkedHashMap<>(), INITIAL_VERSION, null, false);
        }

        Map<String, Object> mappedData = new LinkedHashMap<>();
        if (settings.getData() instanceof Map<?, ?> rawMap) {
            mappedData.putAll((Map<String, Object>) rawMap);
        }

        long version = settings.getVersion() != null ? settings.getVersion() : INITIAL_VERSION;
        return new RawSettingsState(mappedData, version, settings.getUpdatedAt(), true);
    }

    public record RawSettingsState(
            Map<String, Object> data,
            long version,
            Date updatedAt,
            boolean exists
    ) {
    }
}
