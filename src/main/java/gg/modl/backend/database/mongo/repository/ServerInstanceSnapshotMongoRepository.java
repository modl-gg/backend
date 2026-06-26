package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.analytics.data.ServerInstanceSnapshot;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;

import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerInstanceSnapshotFields;
import java.util.Date;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerInstanceSnapshotMongoRepository extends AbstractGlobalMongoRepository<ServerInstanceSnapshot> {
    public ServerInstanceSnapshotMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ServerInstanceSnapshot.class, CollectionName.SERVER_INSTANCE_SNAPSHOTS, tenantMongoAccess);
    }

    public void upsertServerEntry(Date date, String serverId, String serverName, int playerCount,
                                   String platform, String version, String ipAddress,
                                   String pluginVersion, Date createdAt) {
        ServerInstanceSnapshot.ServerEntry entry =
            new ServerInstanceSnapshot.ServerEntry(serverId, serverName, playerCount, platform, version, ipAddress, pluginVersion);

        // Step 1: try to update the existing array element in place (atomic per matched document).
        Query updateQuery = Query.query(
            Criteria.where(ServerInstanceSnapshotFields.DATE).is(date)
                .and("servers").elemMatch(
                    Criteria.where("serverId").is(serverId).and("serverName").is(serverName))
        );
        Update updateExisting = new Update()
            .set("servers.$.playerCount", playerCount)
            .set("servers.$.platform", platform)
            .set("servers.$.version", version)
            .set("servers.$.ipAddress", ipAddress)
            .set("servers.$.pluginVersion", pluginVersion);

        if (updateFirst(updateQuery, updateExisting).getMatchedCount() > 0) {
            return;
        }

        // Step 2: the element is absent — push it, but only into a document that does NOT already
        // contain it. This query is self-excluding, so a concurrent second push into the same
        // bucket cannot double-add the entry. The unique index on `date` resolves the residual
        // create-vs-create race via a swallowed DuplicateKeyException.
        Query insertQuery = Query.query(
            Criteria.where(ServerInstanceSnapshotFields.DATE).is(date)
                .and("servers").not().elemMatch(
                    Criteria.where("serverId").is(serverId).and("serverName").is(serverName))
        );
        Update pushNew = new Update()
            .push(ServerInstanceSnapshotFields.SERVERS, entry)
            .setOnInsert(ServerInstanceSnapshotFields.CREATED_AT, createdAt);
        try {
            upsert(insertQuery, pushNew);
        } catch (DuplicateKeyException e) {
            // A concurrent writer already inserted the bucket document for this date. The unique
            // index on `date` rejected our duplicate insert; the entry is (or will be) present
            // and the next sync cycle reconciles its mutable fields. Safe to ignore.
        }
    }

    public List<ServerInstanceSnapshot> findSinceOrdered(Date startDate) {
        Query query = Query.query(Criteria.where(ServerInstanceSnapshotFields.DATE).gte(startDate));
        query.with(Sort.by(Sort.Direction.ASC, ServerInstanceSnapshotFields.DATE));
        return find(query);
    }

    public void deleteOlderThan(Date cutoff) {
        remove(Query.query(Criteria.where(ServerInstanceSnapshotFields.DATE).lt(cutoff)));
    }
}
