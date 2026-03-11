package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SettingsFields;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.Settings;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class SettingsMongoRepository extends AbstractServerMongoRepository<Settings> {
    public SettingsMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(Settings.class, CollectionName.SETTINGS, tenantMongoAccess);
    }

    public Optional<Settings> findByType(Server server, String type) {
        return findOne(server, Query.query(MongoQueries.where(SettingsFields.TYPE).is(type)));
    }

    public void upsertData(Server server, String type, Map<String, Object> data) {
        upsertRawData(server, type, data);
    }

    public void upsertListData(Server server, String type, Object data) {
        upsertRawData(server, type, data);
    }

    private void upsertRawData(Server server, String type, Object data) {
        Query query = Query.query(MongoQueries.where(SettingsFields.TYPE).is(type));
        Update update = new Update()
                .set(SettingsFields.TYPE, type)
                .set(SettingsFields.DATA, data);
        upsert(server, query, update);
    }

    public void updateDataByType(Server server, String type, Map<String, Object> data) {
        Query query = Query.query(MongoQueries.where(SettingsFields.TYPE).is(type));
        Update update = new Update().set(SettingsFields.DATA, data);
        updateFirst(server, query, update);
    }

    public void removeByType(Server server, String type) {
        remove(server, Query.query(MongoQueries.where(SettingsFields.TYPE).is(type)));
    }
}

