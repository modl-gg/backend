package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.player.data.log.CommandLogDocument;
import org.springframework.stereotype.Repository;

@Repository
public class CommandLogMongoRepository extends AbstractServerMongoRepository<CommandLogDocument> {
    public CommandLogMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(CommandLogDocument.class, CollectionName.COMMAND_LOGS, tenantMongoAccess);
    }
}
