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
public class ServerSettingsTimestampRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerSettingsTimestampRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public void updateStaffPermissionsTimestamp(String serverId, Date timestamp) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.STAFF_PERMISSIONS_UPDATED_AT, timestamp)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }

    public void updatePunishmentTypesTimestamp(String serverId, Date timestamp) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.PUNISHMENT_TYPES_UPDATED_AT, timestamp)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }
}
