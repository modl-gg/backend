package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.CommandLogFields;
import gg.modl.backend.player.data.log.CommandLogDocument;
import gg.modl.backend.server.data.Server;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class CommandLogMongoRepository extends AbstractServerMongoRepository<CommandLogDocument> {
    public CommandLogMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(CommandLogDocument.class, CollectionName.COMMAND_LOGS, tenantMongoAccess);
    }

    public List<CommandLogDocument> findByUuidRecent(Server server, String uuid, int limit) {
        Query query = Query.query(MongoQueries.where(CommandLogFields.UUID).is(uuid));
        query.with(MongoQueries.sort(Sort.Direction.DESC, CommandLogFields.TIMESTAMP));
        query.limit(limit);
        return find(server, query);
    }
}
