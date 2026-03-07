package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoEntityDiffService;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.player.data.log.ChatLogDocument;
import org.springframework.stereotype.Repository;

@Repository
public class ChatLogMongoRepository extends AbstractServerMongoRepository<ChatLogDocument> {
    public ChatLogMongoRepository(TenantMongoAccess tenantMongoAccess, MongoEntityDiffService diffService) {
        super(ChatLogDocument.class, CollectionName.CHAT_LOGS, diffService, tenantMongoAccess);
    }
}