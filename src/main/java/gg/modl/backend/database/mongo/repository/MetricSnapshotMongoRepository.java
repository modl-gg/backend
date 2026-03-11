package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.MetricSnapshotFields;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MetricSnapshotMongoRepository extends AbstractGlobalMongoRepository<MetricSnapshot> {
    public MetricSnapshotMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(MetricSnapshot.class, CollectionName.METRIC_SNAPSHOTS, tenantMongoAccess);
    }

    public List<MetricSnapshot> findSinceOrdered(Date startDate) {
        Query query = Query.query(MongoQueries.where(MetricSnapshotFields.DATE).gte(startDate));
        query.with(MongoQueries.sort(Sort.Direction.ASC, MetricSnapshotFields.DATE));
        return find(query);
    }

    public void upsertSnapshot(Date date, long activeServers, long totalServers, long totalPlayers, long onlinePlayers, Date createdAt) {
        Update update = new Update()
            .set(MetricSnapshotFields.ACTIVE_SERVERS, activeServers)
            .set(MetricSnapshotFields.TOTAL_SERVERS, totalServers)
            .set(MetricSnapshotFields.TOTAL_PLAYERS, totalPlayers)
            .set(MetricSnapshotFields.ONLINE_PLAYERS, onlinePlayers)
            .setOnInsert(MetricSnapshotFields.CREATED_AT, createdAt);
        upsert(Query.query(MongoQueries.where(MetricSnapshotFields.DATE).is(date)), update);
    }

    public void deleteOlderThan(Date cutoff) {
        remove(Query.query(MongoQueries.where(MetricSnapshotFields.DATE).lt(cutoff)));
    }
}
