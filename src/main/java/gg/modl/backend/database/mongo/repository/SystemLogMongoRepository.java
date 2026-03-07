package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import org.springframework.stereotype.Repository;

@Repository
public class SystemLogMongoRepository extends AbstractGlobalMongoRepository<SystemLog> {
    public static final String COLLECTION_NAME = "system_logs";

    public SystemLogMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(SystemLog.class, COLLECTION_NAME, diffService, tenantMongoAccess);
    }
}
