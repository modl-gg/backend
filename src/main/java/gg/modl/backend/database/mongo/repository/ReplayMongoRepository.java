package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ReplayDocumentFields;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.server.data.Server;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayMongoRepository extends AbstractServerMongoRepository<ReplayDocument> {

    public ReplayMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ReplayDocument.class, CollectionName.REPLAYS, tenantMongoAccess);
    }

    public Optional<ReplayDocument> findByReplayId(Server server, String replayId) {
        return findById(server, replayId);
    }

    public Optional<ReplayDocument> replaceLabels(Server server, String replayId, List<ReplayLabel> labels) {
        Query query = Query.query(Criteria.where(ReplayDocumentFields.ID).is(replayId));
        Update update = new Update().set(ReplayDocumentFields.LABELS, labels);
        return Optional.ofNullable(findAndModify(server, query, update, FindAndModifyOptions.options().returnNew(true)));
    }

    public List<ReplayDocument> findByTargetUuid(Server server, String targetUuid, int limit) {
        Query query = Query.query(Criteria.where(ReplayDocumentFields.TARGET_UUID).is(targetUuid));
        query.with(Sort.by(Sort.Direction.DESC, ReplayDocumentFields.CREATED_AT));
        query.limit(Math.min(limit, 100));
        return find(server, query);
    }

    public List<ReplayDocument> findExpiredWithStorageKey(Server server, Date cutoff, ReplayCursor after, int limit) {
        return findExpiredPage(server, expiredWithStorageKey(cutoff), after, limit);
    }

    public List<ReplayDocument> findExpiredWithMissingStorageKey(Server server, Date cutoff, ReplayCursor after, int limit) {
        return findExpiredPage(server, expiredWithMissingStorageKey(cutoff), after, limit);
    }

    private List<ReplayDocument> findExpiredPage(Server server, Criteria base, ReplayCursor after, int limit) {
        Criteria criteria = after == null ? base : new Criteria().andOperator(base, keysetAfter(after));
        Query query = Query.query(criteria);
        query.with(Sort.by(Sort.Order.asc(ReplayDocumentFields.CREATED_AT), Sort.Order.asc(ReplayDocumentFields.ID)));
        query.limit(Math.min(limit, 500));
        return find(server, query);
    }

    private Criteria expiredWithStorageKey(Date cutoff) {
        return new Criteria().andOperator(
            expiredCompletedOrFailed(cutoff),
            Criteria.where(ReplayDocumentFields.STORAGE_KEY).exists(true).nin(null, "")
        );
    }

    private Criteria expiredWithMissingStorageKey(Date cutoff) {
        return new Criteria().andOperator(
            expiredCompletedOrFailed(cutoff),
            new Criteria().orOperator(
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).exists(false),
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).is(null),
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).is("")
            )
        );
    }

    private Criteria expiredCompletedOrFailed(Date cutoff) {
        return new Criteria().andOperator(
            Criteria.where(ReplayDocumentFields.STATUS).in(ReplayDocument.STATUS_COMPLETE, ReplayDocument.STATUS_FAILED),
            Criteria.where(ReplayDocumentFields.CREATED_AT).lt(cutoff)
        );
    }

    private Criteria keysetAfter(ReplayCursor after) {
        return new Criteria().orOperator(
            Criteria.where(ReplayDocumentFields.CREATED_AT).gt(after.createdAt()),
            new Criteria().andOperator(
                Criteria.where(ReplayDocumentFields.CREATED_AT).is(after.createdAt()),
                Criteria.where(ReplayDocumentFields.ID).gt(after.id())
            )
        );
    }

    public List<ReplayDocument> findByStorageKeys(Server server, Collection<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return List.of();
        }
        return find(server, Query.query(Criteria.where(ReplayDocumentFields.STORAGE_KEY).in(storageKeys)));
    }

    public long deleteByReplayIds(Server server, Collection<String> replayIds) {
        if (replayIds == null || replayIds.isEmpty()) {
            return 0L;
        }
        return remove(server, Query.query(Criteria.where(ReplayDocumentFields.ID).in(replayIds))).getDeletedCount();
    }

    public record ReplayCursor(Date createdAt, String id) {
    }
}
