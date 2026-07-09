package gg.modl.backend.database.mongo.repository;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SchedulerLeaseDocumentFields;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseDocument;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SchedulerLeaseRepository extends AbstractGlobalMongoRepository<SchedulerLeaseDocument> {

    public SchedulerLeaseRepository(TenantMongoAccess tenantMongoAccess) {
        super(SchedulerLeaseDocument.class, CollectionName.SCHEDULER_LEASES, tenantMongoAccess);
    }

    public boolean tryAcquire(String leaseName, Instant now, Instant heldUntil, String owner) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(SchedulerLeaseDocumentFields.ID).is(leaseName),
            new Criteria().orOperator(
                Criteria.where(SchedulerLeaseDocumentFields.HELD_UNTIL).exists(false),
                Criteria.where(SchedulerLeaseDocumentFields.HELD_UNTIL).is(null),
                Criteria.where(SchedulerLeaseDocumentFields.HELD_UNTIL).lt(now)
            )
        ));
        Update update = new Update()
            .set(SchedulerLeaseDocumentFields.HELD_UNTIL, heldUntil)
            .set(SchedulerLeaseDocumentFields.OWNER, owner);
        try {
            UpdateResult result = upsert(query, update);
            return result.getUpsertedId() != null || result.getModifiedCount() > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public boolean release(String leaseName, String owner) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(SchedulerLeaseDocumentFields.ID).is(leaseName),
            Criteria.where(SchedulerLeaseDocumentFields.OWNER).is(owner)
        ));
        return remove(query).getDeletedCount() > 0;
    }
}
