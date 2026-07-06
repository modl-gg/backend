package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.replay.data.TrainingSegmentDocument;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingSegmentRepository {
    private static final String NON_TENANT_TRAINING_DATABASE = "training_data";
    private static final String COLLECTION = CollectionName.TRAINING_SEGMENTS;

    private final TenantMongoAccess tenantMongoAccess;

    public TrainingSegmentRepository(TenantMongoAccess tenantMongoAccess) {
        this.tenantMongoAccess = tenantMongoAccess;
    }

    public void save(TrainingSegmentDocument doc) {
        nonTenantTrainingStore().save(doc, COLLECTION);
    }

    private MongoTemplate nonTenantTrainingStore() {
        return tenantMongoAccess.forDatabase(NON_TENANT_TRAINING_DATABASE);
    }
}
