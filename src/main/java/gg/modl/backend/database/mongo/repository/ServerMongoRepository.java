package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.server.data.Server;
import org.springframework.stereotype.Repository;

@Repository
public class ServerMongoRepository extends AbstractGlobalMongoRepository<Server> {
    public ServerMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(Server.class, CollectionName.MODL_SERVERS, diffService, tenantMongoAccess);
    }
}
