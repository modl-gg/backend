package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import java.util.List;
import gg.modl.backend.replay.data.ReplayLabel;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayMongoRepository extends AbstractServerMongoRepository<ReplayDocument> {

    public ReplayMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(ReplayDocument.class, CollectionName.REPLAYS, tenantMongoAccess);
    }

    public Optional<ReplayDocument> findByReplayId(Server server, String replayId) {
        return findById(server, replayId);
    }

    public Optional<ReplayDocument> claimLabels(Server server, String replayId, List<ReplayLabel> labels) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("_id").is(replayId),
            new Criteria().orOperator(
                Criteria.where("labels").exists(false),
                Criteria.where("labels").size(0)
            )
        ));
        Update update = new Update().set("labels", labels);
        return Optional.ofNullable(findAndModify(server, query, update, FindAndModifyOptions.options().returnNew(true)));
    }
}
