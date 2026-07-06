package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.billing.data.StripeWebhookEvent;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.StripeWebhookEventFields;
import java.util.Date;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class StripeWebhookEventMongoRepository extends AbstractGlobalMongoRepository<StripeWebhookEvent> {
    public StripeWebhookEventMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(StripeWebhookEvent.class, CollectionName.STRIPE_WEBHOOK_EVENTS, tenantMongoAccess);
    }

    private static final long PROCESSING_STALE_MS = 15L * 60L * 1000L;
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_PROCESSED = "processed";
    private static final String STATUS_FAILED = "failed";

    public boolean markProcessing(String eventId, String eventType, Date processingAt) {
        try {
            Date staleBefore = new Date(processingAt.getTime() - PROCESSING_STALE_MS);
            Query query = Query.query(new Criteria().andOperator(
                Criteria.where(StripeWebhookEventFields.ID).is(eventId),
                new Criteria().orOperator(
                    Criteria.where(StripeWebhookEventFields.STATUS).exists(false),
                    Criteria.where(StripeWebhookEventFields.STATUS).is(STATUS_FAILED),
                    Criteria.where(StripeWebhookEventFields.STATUS).is(STATUS_PROCESSING).and(StripeWebhookEventFields.PROCESSING_AT).lt(staleBefore)
                )
            ));
            Update update = new Update()
                .setOnInsert(StripeWebhookEventFields.ID, eventId)
                .set(StripeWebhookEventFields.TYPE, eventType)
                .set(StripeWebhookEventFields.STATUS, STATUS_PROCESSING)
                .set(StripeWebhookEventFields.PROCESSING_AT, processingAt)
                .unset(StripeWebhookEventFields.FAILED_AT)
                .unset(StripeWebhookEventFields.ERROR);
            StripeWebhookEvent claimed = findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true)
            );
            return claimed != null && STATUS_PROCESSING.equals(claimed.getStatus());
        } catch (DuplicateKeyException duplicateKeyException) {
            return false;
        }
    }

    public void markProcessed(String eventId, Date processedAt) {
        updateFirst(
            Query.query(Criteria.where(StripeWebhookEventFields.ID).is(eventId)),
            new Update()
                .set(StripeWebhookEventFields.STATUS, STATUS_PROCESSED)
                .set(StripeWebhookEventFields.PROCESSED_AT, processedAt)
                .unset(StripeWebhookEventFields.FAILED_AT)
                .unset(StripeWebhookEventFields.ERROR)
        );
    }

    public void markFailed(String eventId, Date failedAt, String error) {
        updateFirst(
            Query.query(Criteria.where(StripeWebhookEventFields.ID).is(eventId)),
            new Update()
                .set(StripeWebhookEventFields.STATUS, STATUS_FAILED)
                .set(StripeWebhookEventFields.FAILED_AT, failedAt)
                .set(StripeWebhookEventFields.ERROR, error != null ? error : "unknown")
        );
    }
}
