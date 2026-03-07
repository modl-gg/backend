package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import org.springframework.stereotype.Repository;

@Repository
public class SystemConfigMongoRepository extends AbstractGlobalMongoRepository<SystemConfig> {
    private static final String COLLECTION_NAME = "system_config";

    public SystemConfigMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(SystemConfig.class, COLLECTION_NAME, diffService, tenantMongoAccess);
    }
}
