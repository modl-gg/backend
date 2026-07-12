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
    private static final int UPSERT_MAX_ATTEMPTS = 3;

    public ServerInstanceSnapshotMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ServerInstanceSnapshot.class, CollectionName.SERVER_INSTANCE_SNAPSHOTS, tenantMongoAccess);
    }

    public void upsertServerEntry(Date date, String serverId, String serverName, int playerCount,
                                   String platform, String version, String ipAddress,
                                   String pluginVersion, Date createdAt) {
        ServerInstanceSnapshot.ServerEntry entry =
            new ServerInstanceSnapshot.ServerEntry(serverId, serverName, playerCount, platform, version, ipAddress, pluginVersion);
        for (int attempt = 0; attempt < UPSERT_MAX_ATTEMPTS; attempt++) {
            if (tryPersistServerEntry(date, entry, createdAt)) {
                return;
            }
        }
    }

    private boolean tryPersistServerEntry(Date date, ServerInstanceSnapshot.ServerEntry entry, Date createdAt) {
        Query updateQuery = Query.query(
            Criteria.where(ServerInstanceSnapshotFields.DATE).is(date)
                .and(ServerInstanceSnapshotFields.SERVERS).elemMatch(
                    Criteria.where("serverId").is(entry.getServerId()).and("serverName").is(entry.getServerName()))
        );
        Update updateExisting = new Update()
            .set("servers.$.playerCount", entry.getPlayerCount())
            .set("servers.$.platform", entry.getPlatform())
            .set("servers.$.version", entry.getVersion())
            .set("servers.$.ipAddress", entry.getIpAddress())
            .set("servers.$.pluginVersion", entry.getPluginVersion());

        if (updateFirst(updateQuery, updateExisting).getMatchedCount() > 0) {
            return true;
        }

        Query insertQuery = Query.query(
            Criteria.where(ServerInstanceSnapshotFields.DATE).is(date)
                .and(ServerInstanceSnapshotFields.SERVERS).not().elemMatch(
                    Criteria.where("serverId").is(entry.getServerId()).and("serverName").is(entry.getServerName()))
        );
        Update pushNew = new Update()
            .push(ServerInstanceSnapshotFields.SERVERS, entry)
            .setOnInsert(ServerInstanceSnapshotFields.CREATED_AT, createdAt);
        try {
            upsert(insertQuery, pushNew);
            return true;
        } catch (DuplicateKeyException raced) {
            return false;
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
