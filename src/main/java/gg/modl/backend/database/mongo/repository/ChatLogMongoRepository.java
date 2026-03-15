package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.ChatLogFields;
import gg.modl.backend.player.data.log.ChatLogDocument;
import gg.modl.backend.server.data.Server;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ChatLogMongoRepository extends AbstractServerMongoRepository<ChatLogDocument> {
    public ChatLogMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ChatLogDocument.class, CollectionName.CHAT_LOGS, tenantMongoAccess);
    }

    public List<ChatLogDocument> findByUuidRecent(Server server, String uuid, int limit) {
        Query query = Query.query(MongoQueries.where(ChatLogFields.UUID).is(uuid));
        query.with(MongoQueries.sort(Sort.Direction.DESC, ChatLogFields.TIMESTAMP));
        query.limit(limit);
        return find(server, query);
    }
}
