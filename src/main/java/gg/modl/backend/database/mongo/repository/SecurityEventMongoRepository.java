package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import org.bson.Document;
import org.springframework.stereotype.Repository;

@Repository
public class SecurityEventMongoRepository extends AbstractGlobalMongoRepository<Document> {
    public static final String COLLECTION_NAME = "security_events";

    public SecurityEventMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Document.class, COLLECTION_NAME, diffService, tenantMongoAccess);
    }
}
