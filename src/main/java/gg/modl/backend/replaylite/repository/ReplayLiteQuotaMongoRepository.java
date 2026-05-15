package gg.modl.backend.replaylite.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.replaylite.data.ReplayLiteDailyQuotaDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    public boolean reserveConfirmedUpload(UUID pluginServerUuid, LocalDate day, int limit, Instant now) {
        String id = quotaId(pluginServerUuid, day);
        Query query = Query.query(Criteria.where("_id").is(id).and("count").lt(limit));
        Update update = new Update()
            .inc("count", 1)
            .set("updatedAt", now)
            .setOnInsert("pluginServerUuid", pluginServerUuid)
            .setOnInsert("day", day)
            .setOnInsert("createdAt", now)
            .setOnInsert("expiresAt", day.plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC));

        try {
            return findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true)
            ) != null;
        } catch (DuplicateKeyException e) {
            return findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true)
            ) != null;
        }
    }

    public void releaseConfirmedUpload(UUID pluginServerUuid, LocalDate day, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(quotaId(pluginServerUuid, day)).and("count").gt(0));
        Update update = new Update()
            .inc("count", -1)
            .set("updatedAt", now);
        updateFirst(query, update);
    }

    private String quotaId(UUID pluginServerUuid, LocalDate day) {
        return pluginServerUuid + ":" + day;
    }
}
