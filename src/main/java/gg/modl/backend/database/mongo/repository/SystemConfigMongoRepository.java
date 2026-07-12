package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SystemConfigFields;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigMongoRepository extends AbstractGlobalMongoRepository<SystemConfig> {
    public static final String MAIN_CONFIG_ID = "main_config";

    public SystemConfigMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemConfig.class, CollectionName.SYSTEM_CONFIG, tenantMongoAccess);
    }

    public Optional<SystemConfig> findMainConfig() {
        return findOne(Query.query(Criteria.where(SystemConfigFields.CONFIG_ID).is(MAIN_CONFIG_ID)));
    }
}

