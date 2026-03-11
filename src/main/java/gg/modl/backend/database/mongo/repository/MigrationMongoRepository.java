package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.server.data.Server;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MigrationMongoRepository extends AbstractServerMongoRepository<MigrationStatus> {
    private static final String COLLECTION_NAME = "migrations";

    public MigrationMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(MigrationStatus.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public Optional<MigrationStatus> findLatest(Server server) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(1);
        return findOne(server, query);
    }

    public Optional<MigrationStatus> findLatestCompletedOrFailed(Server server) {
        Query query = Query.query(Criteria.where("status").in("completed", "failed"))
            .with(Sort.by(Sort.Direction.DESC, "completedAt")).limit(1);
        return findOne(server, query);
    }

    public Optional<MigrationStatus> findActiveMigration(Server server) {
        Query query = Query.query(Criteria.where("status").in("building_json", "uploading_json", "processing_data"));
        return findOne(server, query);
    }

    public boolean existsActiveMigration(Server server) {
        Query query = Query.query(Criteria.where("status").in("building_json", "uploading_json", "processing_data"));
        return exists(server, query);
    }

    public void updateById(Server server, String id, Update update) {
        updateFirst(server, Query.query(Criteria.where("_id").is(id)), update);
    }
}
