package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ReplayDocumentFields;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import java.util.List;
import java.util.Optional;
import gg.modl.backend.replay.data.ReplayLabel;
import java.util.Date;
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

    public Optional<ReplayDocument> claimLabels(Server server, String replayId, List<ReplayLabel> labels) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ReplayDocumentFields.ID).is(replayId),
            Criteria.where(ReplayDocumentFields.STATUS).is(ReplayDocument.STATUS_COMPLETE),
            new Criteria().orOperator(
                Criteria.where(ReplayDocumentFields.LABELS).exists(false),
                Criteria.where(ReplayDocumentFields.LABELS).size(0)
            )
        ));
        Update update = new Update().set(ReplayDocumentFields.LABELS, labels);
        return Optional.ofNullable(findAndModify(server, query, update, FindAndModifyOptions.options().returnNew(true)));
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

    public List<ReplayDocument> findExpiredCompletedOrFailed(Server server, Date cutoff, int limit) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ReplayDocumentFields.STATUS).in(ReplayDocument.STATUS_COMPLETE, ReplayDocument.STATUS_FAILED),
            Criteria.where(ReplayDocumentFields.CREATED_AT).lt(cutoff),
            Criteria.where(ReplayDocumentFields.STORAGE_KEY).exists(true).nin(null, "")
        ));
        query.with(Sort.by(Sort.Direction.ASC, ReplayDocumentFields.CREATED_AT));
        query.limit(Math.min(limit, 500));
        return find(server, query);
    }

    public List<ReplayDocument> findExpiredCompletedOrFailedAfter(Server server, Date cutoff, Date afterExclusive, int limit) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ReplayDocumentFields.STATUS).in(ReplayDocument.STATUS_COMPLETE, ReplayDocument.STATUS_FAILED),
            Criteria.where(ReplayDocumentFields.CREATED_AT).gt(afterExclusive).lt(cutoff),
            Criteria.where(ReplayDocumentFields.STORAGE_KEY).exists(true).nin(null, "")
        ));
        query.with(Sort.by(Sort.Direction.ASC, ReplayDocumentFields.CREATED_AT));
        query.limit(Math.min(limit, 500));
        return find(server, query);
    }

    public long countExpiredWithMissingStorageKey(Server server, Date cutoff) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ReplayDocumentFields.STATUS).in(ReplayDocument.STATUS_COMPLETE, ReplayDocument.STATUS_FAILED),
            Criteria.where(ReplayDocumentFields.CREATED_AT).lt(cutoff),
            new Criteria().orOperator(
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).exists(false),
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).is(null),
                Criteria.where(ReplayDocumentFields.STORAGE_KEY).is("")
            )
        ));
        return count(server, query);
    }

    public boolean deleteByReplayId(Server server, String replayId) {
        return remove(server, Query.query(Criteria.where(ReplayDocumentFields.ID).is(replayId))).getDeletedCount() > 0;
    }
}
