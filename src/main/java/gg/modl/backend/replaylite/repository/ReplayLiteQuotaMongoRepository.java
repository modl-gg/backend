package gg.modl.backend.replaylite.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ReplayLiteDailyQuotaDocumentFields;
import gg.modl.backend.replaylite.data.ReplayLiteDailyQuotaDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayLiteQuotaMongoRepository extends AbstractGlobalMongoRepository<ReplayLiteDailyQuotaDocument> {

    public ReplayLiteQuotaMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ReplayLiteDailyQuotaDocument.class, CollectionName.REPLAY_LITE_DAILY_QUOTAS, tenantMongoAccess);
    }

    public QuotaReservationResult reserveConfirmedUpload(
        UUID pluginServerUuid,
        LocalDate day,
        String replayId,
        int limit,
        Instant now
    ) {
        String id = quotaId(pluginServerUuid, day);

        try {
            if (tryReserve(id, pluginServerUuid, day, replayId, limit, now, true)) {
                return QuotaReservationResult.RESERVED;
            }
        } catch (DuplicateKeyException e) {
            if (tryReserve(id, pluginServerUuid, day, replayId, limit, now, false)) {
                return QuotaReservationResult.RESERVED;
            }
        }
        return classifyFailedReservation(id, replayId);
    }

    public void releaseConfirmedUpload(UUID pluginServerUuid, LocalDate day, String replayId, Instant now) {
        Query query = Query.query(Criteria.where(ReplayLiteDailyQuotaDocumentFields.ID).is(quotaId(pluginServerUuid, day))
            .and(ReplayLiteDailyQuotaDocumentFields.COUNT).gt(0)
            .and(ReplayLiteDailyQuotaDocumentFields.REPLAY_IDS).is(replayId));
        Update update = new Update()
            .inc(ReplayLiteDailyQuotaDocumentFields.COUNT, -1)
            .pull(ReplayLiteDailyQuotaDocumentFields.REPLAY_IDS, replayId)
            .set(ReplayLiteDailyQuotaDocumentFields.UPDATED_AT, now);
        updateFirst(query, update);
    }

    private boolean tryReserve(
        String id,
        UUID pluginServerUuid,
        LocalDate day,
        String replayId,
        int limit,
        Instant now,
        boolean upsert
    ) {
        Query query = Query.query(Criteria.where(ReplayLiteDailyQuotaDocumentFields.ID).is(id)
            .and(ReplayLiteDailyQuotaDocumentFields.COUNT).lt(limit)
            .and(ReplayLiteDailyQuotaDocumentFields.REPLAY_IDS).ne(replayId));
        Update update = new Update()
            .inc(ReplayLiteDailyQuotaDocumentFields.COUNT, 1)
            .addToSet(ReplayLiteDailyQuotaDocumentFields.REPLAY_IDS, replayId)
            .set(ReplayLiteDailyQuotaDocumentFields.UPDATED_AT, now)
            .setOnInsert(ReplayLiteDailyQuotaDocumentFields.PLUGIN_SERVER_UUID, pluginServerUuid)
            .setOnInsert(ReplayLiteDailyQuotaDocumentFields.DAY, day)
            .setOnInsert(ReplayLiteDailyQuotaDocumentFields.CREATED_AT, now)
            .setOnInsert(ReplayLiteDailyQuotaDocumentFields.EXPIRES_AT, day.plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC));

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        if (upsert) {
            options = options.upsert(true);
        }
        return findAndModify(query, update, options) != null;
    }

    private QuotaReservationResult classifyFailedReservation(String id, String replayId) {
        return findById(id)
            .filter(document -> containsReplayId(document.getReplayIds(), replayId))
            .map(document -> QuotaReservationResult.ALREADY_RESERVED)
            .orElse(QuotaReservationResult.LIMIT_REACHED);
    }

    private boolean containsReplayId(List<String> replayIds, String replayId) {
        return replayIds != null && replayIds.contains(replayId);
    }

    private String quotaId(UUID pluginServerUuid, LocalDate day) {
        return pluginServerUuid + ":" + day;
    }

    public enum QuotaReservationResult {
        RESERVED,
        ALREADY_RESERVED,
        LIMIT_REACHED
    }
}
