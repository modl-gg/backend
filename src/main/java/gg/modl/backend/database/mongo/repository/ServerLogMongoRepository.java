package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ServerLogFields;
import gg.modl.backend.log.data.SystemLog;
import gg.modl.backend.server.data.Server;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ServerLogMongoRepository extends AbstractServerMongoRepository<SystemLog> {
    public ServerLogMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemLog.class, CollectionName.LOGS, tenantMongoAccess);
    }

    public List<SystemLog> findRecent(Server server, int limit) {
        Query query = new Query()
                .with(MongoQueries.sort(Sort.Direction.DESC, ServerLogFields.CREATED))
                .limit(limit);
        return find(server, query);
    }
}
