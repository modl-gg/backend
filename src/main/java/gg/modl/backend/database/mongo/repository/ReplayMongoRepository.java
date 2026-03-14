package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayMongoRepository extends AbstractServerMongoRepository<ReplayDocument> {

    public ReplayMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ReplayDocument.class, CollectionName.REPLAYS, tenantMongoAccess);
    }

    public Optional<ReplayDocument> findByReplayId(Server server, String replayId) {
        return findById(server, replayId);
    }
}
