package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerLogFields;
import gg.modl.backend.log.data.ServerLog;
import gg.modl.backend.server.data.Server;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ServerLogMongoRepository extends AbstractServerMongoRepository<ServerLog> {
    public ServerLogMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ServerLog.class, CollectionName.LOGS, tenantMongoAccess);
    }

    public List<ServerLog> findRecent(Server server, int limit) {
        Query query = new Query()
            .with(Sort.by(Sort.Direction.DESC, ServerLogFields.CREATED))
            .limit(limit);
        return find(server, query);
    }
}
