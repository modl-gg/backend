package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerFields;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ServerActivityRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerActivityRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public void updateActivity(Server server, Date lastActivityAt) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(server.getId())),
            new Update().set(ServerFields.LAST_ACTIVITY_AT, lastActivityAt)
        );
    }

    public void updateActivityAndPlayerCount(Server server, Date lastActivityAt, long onlinePlayerCount) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(server.getId())),
            new Update()
                .set(ServerFields.LAST_ACTIVITY_AT, lastActivityAt)
                .set(ServerFields.ONLINE_PLAYER_COUNT, onlinePlayerCount)
        );
    }
}
