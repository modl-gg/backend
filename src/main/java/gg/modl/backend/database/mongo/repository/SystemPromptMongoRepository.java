package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import org.springframework.stereotype.Repository;

@Repository
public class SystemPromptMongoRepository extends AbstractGlobalMongoRepository<SystemPrompt> {
    private static final String COLLECTION_NAME = "systemprompts";

    public SystemPromptMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(SystemPrompt.class, COLLECTION_NAME, diffService, tenantMongoAccess);
    }
}
