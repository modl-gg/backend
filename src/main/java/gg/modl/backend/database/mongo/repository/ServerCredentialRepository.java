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
public class ServerCredentialRepository extends AbstractGlobalMongoRepository<Server> {

    public ServerCredentialRepository(TenantMongoAccess tenantMongoAccess) {
        super(Server.class, CollectionName.MODL_SERVERS, tenantMongoAccess);
    }

    public void updateAdminEmail(String serverId, String adminEmail) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update()
                .set(ServerFields.ADMIN_EMAIL, adminEmail)
                .set(ServerFields.UPDATED_AT, new Date())
        );
    }

    public void updateApiKey(String serverId, String apiKey) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update().set(ServerFields.API_KEY, apiKey)
        );
    }

    public void clearApiKey(String serverId) {
        updateFirst(
            Query.query(Criteria.where(ServerFields.ID).is(serverId)),
            new Update().unset(ServerFields.API_KEY)
        );
    }
}
