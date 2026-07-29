package gg.modl.backend.database.mongo.repository;

import static gg.modl.backend.database.mongo.MongoAggregationResults.extractLong;

import com.mongodb.client.result.UpdateResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.SetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerUsageRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerUsageRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public List<Server> findUsageRefreshCandidates(Date staleCutoff, int limit) {
        Criteria staleCriteria = new Criteria().orOperator(
            Criteria.where(ServerFields.LAST_STATS_UPDATED_AT).exists(false),
            Criteria.where(ServerFields.LAST_STATS_UPDATED_AT).lt(staleCutoff)
        );

        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.DATABASE_NAME).ne(null),
            Criteria.where(ServerFields.PROVISIONING_STATUS).is(ProvisioningStatus.COMPLETED),
            Criteria.where(ServerFields.EMAIL_VERIFIED).is(true),
            staleCriteria
        ));
        query.with(Sort.by(Sort.Direction.ASC, ServerFields.LAST_STATS_UPDATED_AT));
        query.limit(limit);
        query.fields()
            .include(ServerFields.SERVER_NAME)
            .include(ServerFields.CUSTOM_DOMAIN)
            .include(ServerFields.DATABASE_NAME)
            .include(ServerFields.ADMIN_EMAIL)
            .include(ServerFields.EMAIL_VERIFIED)
            .include(ServerFields.PLAN)
            .include(ServerFields.USER_COUNT)
            .include(ServerFields.TICKET_COUNT)
            .include(ServerFields.LAST_STATS_UPDATED_AT)
            .include(ServerFields.LAST_ACTIVITY_AT)
            .include(ServerFields.UPDATED_AT);
        return find(query);
    }

    public List<Server> findUsageTargetsByIds(List<String> serverIds) {
        Query query = Query.query(Criteria.where(ServerFields.ID).in(serverIds));
        query.fields()
            .include(ServerFields.SERVER_NAME)
            .include(ServerFields.CUSTOM_DOMAIN)
            .include(ServerFields.DATABASE_NAME)
            .include(ServerFields.ADMIN_EMAIL)
            .include(ServerFields.EMAIL_VERIFIED)
            .include(ServerFields.PLAN)
            .include(ServerFields.USER_COUNT)
            .include(ServerFields.TICKET_COUNT)
            .include(ServerFields.LAST_STATS_UPDATED_AT)
            .include(ServerFields.LAST_ACTIVITY_AT)
            .include(ServerFields.UPDATED_AT);
        return find(query);
    }

    public void incrementAiRequests(String serverId, long additionalRequests) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update().inc(ServerFields.AI_REQUESTS_CURRENT_PERIOD, additionalRequests)
        );
    }

    public void incrementStorageUsed(String serverId, long bytes) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update().inc(ServerFields.STORAGE_USED_BYTES, bytes)
        );
    }

    public boolean tryIncrementStorageUsedWithinLimit(String serverId, long bytes, long maxBytes) {
        long maxCurrentBytes = maxBytes - bytes;
        if (bytes < 0 || maxCurrentBytes < 0) {
            return false;
        }
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            new Criteria().orOperator(
                Criteria.where(ServerFields.STORAGE_USED_BYTES).lte(maxCurrentBytes),
                Criteria.where(ServerFields.STORAGE_USED_BYTES).exists(false),
                Criteria.where(ServerFields.STORAGE_USED_BYTES).is(null)
            )
        ));
        UpdateResult result = updateFirst(query, new Update().inc(ServerFields.STORAGE_USED_BYTES, bytes));
        return result.getMatchedCount() == 1;
    }

    public void decrementStorageUsed(String serverId, long bytes) {
        AggregationUpdate update = AggregationUpdate.update().set(
            SetOperation.set(ServerFields.STORAGE_USED_BYTES).toValueOf(flooredStorageAfterDecrement(bytes))
        );
        globalTemplate().updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            update,
            collectionName()
        );
    }

    private AggregationExpression flooredStorageAfterDecrement(long bytes) {
        return context -> new Document("$max", List.of(0L, new Document("$subtract",
            List.of("$" + ServerFields.STORAGE_USED_BYTES, bytes))));
    }

    public void setStorageUsed(String serverId, long bytes) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update().set(ServerFields.STORAGE_USED_BYTES, bytes)
        );
    }

    public boolean setStorageUsedIfBelow(String serverId, long bytes) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(ServerFields.ID).is(serverId),
            new Criteria().orOperator(
                Criteria.where(ServerFields.STORAGE_USED_BYTES).lt(bytes),
                Criteria.where(ServerFields.STORAGE_USED_BYTES).exists(false)
            )
        ));
        UpdateResult result = updateFirst(query, new Update().set(ServerFields.STORAGE_USED_BYTES, bytes));
        return result.getModifiedCount() == 1;
    }

    public Optional<AIUsageSnapshot> findAIUsageSnapshotById(String serverId) {
        Query query = Query.query(Criteria.where(ServerFields.ID).is(serverId));
        query.fields()
            .include(ServerFields.AI_REQUESTS_CURRENT_PERIOD)
            .include(ServerFields.MAX_AI_OVERAGE_REQUESTS);

        Document document = globalTemplate().findOne(query, Document.class, collectionName());
        if (document == null) {
            return Optional.empty();
        }

        return Optional.of(new AIUsageSnapshot(
            extractLong(document, ServerFields.AI_REQUESTS_CURRENT_PERIOD),
            extractLong(document, ServerFields.MAX_AI_OVERAGE_REQUESTS)
        ));
    }

    public void resetUsageCounters(String serverId) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.AI_REQUESTS_CURRENT_PERIOD, 0L)
        );
    }

    public void resetUsageAndStatsCounters(String serverId) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.STORAGE_USED_BYTES, 0L)
                .set(ServerFields.USER_COUNT, 0L)
                .set(ServerFields.TICKET_COUNT, 0L)
                .set(ServerFields.ONLINE_PLAYER_COUNT, 0L)
                .set(ServerFields.AI_REQUESTS_CURRENT_PERIOD, 0L)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }

    public void updateUsageStats(String serverId, long userCount, long ticketCount, Date updatedAt) {
        Update update = new Update()
            .set(ServerFields.USER_COUNT, userCount)
            .set(ServerFields.TICKET_COUNT, ticketCount)
            .set(ServerFields.LAST_STATS_UPDATED_AT, updatedAt);
        updateFirst(Query.query(Criteria.where(ServerFields.ID).is(serverId)), update);
    }

    public record AIUsageSnapshot(long aiRequestsCurrentPeriod, long maxAiOverageRequests) {}
}
