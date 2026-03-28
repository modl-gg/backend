package gg.modl.backend.database.mongo.repository;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SettingsFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsMongoRepository extends AbstractServerMongoRepository<Settings> {
    public SettingsMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Settings.class, CollectionName.SETTINGS, tenantMongoAccess);
    }

    public boolean existsByType(Server server, String type) {
        return exists(server, Query.query(Criteria.where(SettingsFields.TYPE).is(type)));
    }

    public Optional<Settings> findByType(Server server, String type) {
        return findOne(server, Query.query(Criteria.where(SettingsFields.TYPE).is(type)));
    }

    public void upsertData(Server server, String type, Map<String, Object> data) {
        upsertRawData(server, type, data);
    }

    private void upsertRawData(Server server, String type, Object data) {
        Query query = Query.query(Criteria.where(SettingsFields.TYPE).is(type));
        Update update = new Update()
            .set(SettingsFields.TYPE, type)
            .set(SettingsFields.DATA, data);
        upsert(server, query, update);
    }

    public void upsertListData(Server server, String type, Object data) {
        upsertRawData(server, type, data);
    }

    public void updateDataByType(Server server, String type, Map<String, Object> data) {
        Query query = Query.query(Criteria.where(SettingsFields.TYPE).is(type));
        Update update = new Update().set(SettingsFields.DATA, data);
        updateFirst(server, query, update);
    }

    public void removeByType(Server server, String type) {
        remove(server, Query.query(Criteria.where(SettingsFields.TYPE).is(type)));
    }

    public List<Settings> findLatestByType(Server server, String type, int limit) {
        Query query = Query.query(Criteria.where(SettingsFields.TYPE).is(type))
            .with(Sort.by(
                Sort.Order.desc(SettingsFields.VERSION),
                Sort.Order.desc(SettingsFields.UPDATED_AT),
                Sort.Order.desc(SettingsFields.ID)
            ))
            .limit(limit);
        return find(server, query);
    }

    public boolean updateWithVersionCheck(Server server, String settingsId, long expectedVersion,
                                          String type, Map<String, Object> data, long newVersion, Date updatedAt) {
        Criteria versionCriteria = buildVersionCriteria(expectedVersion);
        Query updateQuery = Query.query(Criteria.where(SettingsFields.ID).is(settingsId)
            .andOperator(versionCriteria));
        Update update = new Update()
            .set(SettingsFields.TYPE, type)
            .set(SettingsFields.DATA, data)
            .set(SettingsFields.VERSION, newVersion)
            .set(SettingsFields.UPDATED_AT, updatedAt);
        UpdateResult result = updateFirst(server, updateQuery, update);
        return result.getModifiedCount() > 0;
    }

    private Criteria buildVersionCriteria(long expectedVersion) {
        if (expectedVersion == 0L) {
            return new Criteria().orOperator(
                Criteria.where(SettingsFields.VERSION).is(0L),
                Criteria.where(SettingsFields.VERSION).exists(false),
                Criteria.where(SettingsFields.VERSION).is(null)
            );
        }
        return Criteria.where(SettingsFields.VERSION).is(expectedVersion);
    }
}

