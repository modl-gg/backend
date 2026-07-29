package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.TrainingSegmentDocumentFields;
import gg.modl.backend.replay.data.TrainingSegmentDocument;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TrainingSegmentRepository {
    private static final String COLLECTION = CollectionName.TRAINING_SEGMENTS;

    private final TenantMongoAccess tenantMongoAccess;

    public void save(TrainingSegmentDocument doc) {
        nonTenantTrainingStore().save(doc, COLLECTION);
    }

    public long deleteByReplayId(String serverDatabaseName, String replayId) {
        if (replayId == null || replayId.isBlank()) {
            return 0L;
        }
        return deleteByReplayIds(serverDatabaseName, List.of(replayId));
    }

    public long deleteByReplayIds(String serverDatabaseName, Collection<String> replayIds) {
        if (replayIds == null || replayIds.isEmpty()) {
            return 0L;
        }
        Query query = Query.query(Criteria.where(TrainingSegmentDocumentFields.SERVER_DATABASE_NAME).is(serverDatabaseName)
            .and(TrainingSegmentDocumentFields.REPLAY_ID).in(replayIds));
        return nonTenantTrainingStore().remove(query, TrainingSegmentDocument.class, COLLECTION).getDeletedCount();
    }

    private MongoTemplate nonTenantTrainingStore() {
        return tenantMongoAccess.forDatabase(CollectionName.TRAINING_DATABASE);
    }
}
