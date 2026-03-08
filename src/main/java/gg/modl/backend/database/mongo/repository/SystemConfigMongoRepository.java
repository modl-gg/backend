package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.SystemConfigFields;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SystemConfigMongoRepository extends AbstractGlobalMongoRepository<SystemConfig> {
    public static final String MAIN_CONFIG_ID = "main_config";
    private static final String COLLECTION_NAME = "system_config";

    public SystemConfigMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemConfig.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public Optional<SystemConfig> findMainConfig() {
        return findOne(Query.query(MongoQueries.where(SystemConfigFields.CONFIG_ID).is(MAIN_CONFIG_ID)));
    }
}

