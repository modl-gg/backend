package gg.modl.backend.settings.service;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    private final DynamicMongoTemplateProvider mongoProvider;

    public RawSettingsState getRawState(Server server, String type) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Settings settings = findLatestSettingsDocument(template, type);
        return toRawState(settings);
    }

    public RawSettingsState saveRawState(Server server, String type, long expectedVersion, Map<String, Object> data) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Settings current = findLatestSettingsDocument(template, type);
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
                template.save(inserted, CollectionName.SETTINGS);
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

        Query updateQuery = Query.query(Criteria.where("_id").is(current.getId())
                .andOperator(versionCriteria(expectedVersion)));
        Update update = new Update()
                .set("type", type)
                .set("data", normalizedData)
                .set("version", expectedVersion + 1)
                .set("updatedAt", now);

        UpdateResult result = template.updateFirst(updateQuery, update, Settings.class, CollectionName.SETTINGS);
        if (result.getModifiedCount() == 0) {
            RawSettingsState latest = getRawState(server, type);
            throw new SettingsConflictException(
                    "Settings were modified by another user. Reload and retry.",
                    latest.version()
            );
        }

        return new RawSettingsState(normalizedData, expectedVersion + 1, now, true);
    }

    private Settings findLatestSettingsDocument(MongoTemplate template, String type) {
        Query query = Query.query(Criteria.where("type").is(type))
                .with(Sort.by(
                        Sort.Order.desc("version"),
                        Sort.Order.desc("updatedAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(2);
        List<Settings> matches = template.find(query, Settings.class, CollectionName.SETTINGS);
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
                    Criteria.where("version").is(INITIAL_VERSION),
                    Criteria.where("version").exists(false),
                    Criteria.where("version").is(null)
            );
        }
        return Criteria.where("version").is(expectedVersion);
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
