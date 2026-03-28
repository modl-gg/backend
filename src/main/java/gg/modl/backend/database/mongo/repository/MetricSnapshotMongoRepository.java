package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.analytics.data.MetricSnapshot;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.MetricSnapshotFields;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MetricSnapshotMongoRepository extends AbstractGlobalMongoRepository<MetricSnapshot> {
    public MetricSnapshotMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(MetricSnapshot.class, CollectionName.METRIC_SNAPSHOTS, tenantMongoAccess);
    }

    public List<MetricSnapshot> findSinceOrdered(Date startDate) {
        Query query = Query.query(Criteria.where(MetricSnapshotFields.DATE).gte(startDate));
        query.with(Sort.by(Sort.Direction.ASC, MetricSnapshotFields.DATE));
        return find(query);
    }

    public void upsertSnapshot(Date date, long activeServers, Date createdAt) {
        Update update = new Update()
            .set(MetricSnapshotFields.ACTIVE_SERVERS, activeServers)
            .setOnInsert(MetricSnapshotFields.CREATED_AT, createdAt);
        upsert(Query.query(Criteria.where(MetricSnapshotFields.DATE).is(date)), update);
    }

    public void deleteOlderThan(Date cutoff) {
        remove(Query.query(Criteria.where(MetricSnapshotFields.DATE).lt(cutoff)));
    }
}
