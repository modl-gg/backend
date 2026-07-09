package gg.modl.backend.replaylite.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ReplayLiteDocumentFields;
import gg.modl.backend.replaylite.data.ReplayLiteDocument;
import gg.modl.backend.replaylite.data.ReplayLiteLabel;
import gg.modl.backend.replaylite.data.ReplayLiteStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayLiteMongoRepository extends AbstractGlobalMongoRepository<ReplayLiteDocument> {

    public ReplayLiteMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ReplayLiteDocument.class, CollectionName.REPLAY_LITE_REPLAYS, tenantMongoAccess);
    }

    public Optional<ReplayLiteDocument> findByReplayId(String replayId) {
        return findById(replayId);
    }

    public long countConfirmedForServerBetween(UUID pluginServerUuid, Instant startInclusive, Instant endExclusive) {
        Query query = Query.query(Criteria.where(ReplayLiteDocumentFields.PLUGIN_SERVER_UUID).is(pluginServerUuid)
            .and(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.CONFIRMED)
            .and(ReplayLiteDocumentFields.CONFIRMED_AT).gte(startInclusive).lt(endExclusive));
        return count(query);
    }

    public long countPendingForServerSince(UUID pluginServerUuid, Instant staleCutoff) {
        Query query = Query.query(Criteria.where(ReplayLiteDocumentFields.PLUGIN_SERVER_UUID).is(pluginServerUuid)
            .and(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.PENDING)
            .and(ReplayLiteDocumentFields.CREATED_AT).gte(staleCutoff));
        return count(query);
    }

    public List<ReplayLiteDocument> findExpiredConfirmed(Instant now, ReplayLiteCursor after, int limit) {
        Criteria base = Criteria.where(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.CONFIRMED)
            .and(ReplayLiteDocumentFields.EXPIRES_AT).lte(now);
        return findPage(base, ReplayLiteDocumentFields.EXPIRES_AT, after, limit);
    }

    public List<ReplayLiteDocument> findStalePending(Instant staleCutoff, ReplayLiteCursor after, int limit) {
        Criteria base = Criteria.where(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.PENDING)
            .and(ReplayLiteDocumentFields.CREATED_AT).lte(staleCutoff);
        return findPage(base, ReplayLiteDocumentFields.CREATED_AT, after, limit);
    }

    private List<ReplayLiteDocument> findPage(Criteria base, String sortField, ReplayLiteCursor after, int limit) {
        Criteria criteria = after == null ? base : new Criteria().andOperator(base, keysetAfter(sortField, after));
        Query query = Query.query(criteria);
        query.with(Sort.by(Sort.Order.asc(sortField), Sort.Order.asc(ReplayLiteDocumentFields.ID)));
        query.limit(limit);
        return find(query);
    }

    private Criteria keysetAfter(String sortField, ReplayLiteCursor after) {
        return new Criteria().orOperator(
            Criteria.where(sortField).gt(after.sortValue()),
            new Criteria().andOperator(
                Criteria.where(sortField).is(after.sortValue()),
                Criteria.where(ReplayLiteDocumentFields.ID).gt(after.id())
            )
        );
    }

    public ReplayLiteDocument saveEntity(ReplayLiteDocument document) {
        return super.saveEntity(document);
    }

    public boolean claimLabels(String replayId, Instant now, List<ReplayLiteLabel> labels, String labelIp) {
        Query query = Query.query(Criteria.where(ReplayLiteDocumentFields.ID).is(replayId)
            .and(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.CONFIRMED)
            .and(ReplayLiteDocumentFields.EXPIRES_AT).gt(now)
            .orOperator(
                Criteria.where(ReplayLiteDocumentFields.LABELS).exists(false),
                Criteria.where(ReplayLiteDocumentFields.LABELS).is(null),
                Criteria.where(ReplayLiteDocumentFields.LABELS).size(0)
            ));
        Update update = new Update()
            .set(ReplayLiteDocumentFields.LABELS, labels)
            .set(ReplayLiteDocumentFields.LABEL_IP, labelIp)
            .set(ReplayLiteDocumentFields.LABELED_AT, now);

        UpdateResult result = updateFirst(query, update);
        return result.getModifiedCount() == 1;
    }

    public boolean confirmPendingUpload(
        String replayId,
        long confirmedSize,
        Instant confirmedAt,
        Instant expiresAt,
        String confirmIp,
        Instant freshCreatedAfter
    ) {
        Query query = Query.query(Criteria.where(ReplayLiteDocumentFields.ID).is(replayId)
            .and(ReplayLiteDocumentFields.STATUS).is(ReplayLiteStatus.PENDING)
            .and(ReplayLiteDocumentFields.CREATED_AT).gt(freshCreatedAfter));
        Update update = new Update()
            .set(ReplayLiteDocumentFields.STATUS, ReplayLiteStatus.CONFIRMED)
            .set(ReplayLiteDocumentFields.CONFIRMED_SIZE, confirmedSize)
            .set(ReplayLiteDocumentFields.CONFIRMED_AT, confirmedAt)
            .set(ReplayLiteDocumentFields.EXPIRES_AT, expiresAt)
            .set(ReplayLiteDocumentFields.CONFIRM_IP, confirmIp);

        UpdateResult result = updateFirst(query, update);
        return result.getModifiedCount() == 1;
    }

    public void deleteByReplayId(String replayId) {
        remove(Query.query(Criteria.where(ReplayLiteDocumentFields.ID).is(replayId)));
    }

    public record ReplayLiteCursor(Instant sortValue, String id) {
    }
}
