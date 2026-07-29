package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.MigrationStatusFields;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MigrationMongoRepository extends AbstractServerMongoRepository<MigrationStatus> {
    private static final List<String> ACTIVE_STATUSES =
        List.of("building_json", "uploading_json", "processing_data");

    public MigrationMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(MigrationStatus.class, CollectionName.MIGRATIONS, tenantMongoAccess);
    }

    public Optional<MigrationStatus> findLatest(Server server) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, MigrationStatusFields.STARTED_AT)).limit(1);
        return findOne(server, query);
    }

    public Optional<MigrationStatus> findLatestCompletedOrFailed(Server server) {
        Query query = Query.query(Criteria.where(MigrationStatusFields.STATUS).in("completed", "failed").and(MigrationStatusFields.COOLDOWN_EXEMPT).ne(true))
            .with(Sort.by(Sort.Direction.DESC, MigrationStatusFields.COMPLETED_AT)).limit(1);
        return findOne(server, query);
    }

    public Optional<MigrationStatus> findActiveMigration(Server server) {
        Query query = Query.query(Criteria.where(MigrationStatusFields.STATUS).in(ACTIVE_STATUSES))
            .with(Sort.by(Sort.Direction.DESC, MigrationStatusFields.STARTED_AT)).limit(1);
        return findOne(server, query);
    }

    public Optional<MigrationStatus> findActiveMigration(Server server, Date staleBefore) {
        Query query = Query.query(Criteria.where(MigrationStatusFields.STATUS).in(ACTIVE_STATUSES).and(MigrationStatusFields.STARTED_AT).gte(staleBefore))
            .with(Sort.by(Sort.Direction.DESC, MigrationStatusFields.STARTED_AT)).limit(1);
        return findOne(server, query);
    }

    public boolean existsActiveMigration(Server server) {
        Query query = Query.query(Criteria.where(MigrationStatusFields.STATUS).in(ACTIVE_STATUSES));
        return exists(server, query);
    }

    public boolean existsActiveMigration(Server server, Date staleBefore) {
        Query query = Query.query(Criteria.where(MigrationStatusFields.STATUS).in(ACTIVE_STATUSES).and(MigrationStatusFields.STARTED_AT).gte(staleBefore));
        return exists(server, query);
    }

    public long failStaleMigrations(Server server, Date staleBefore, Date now, String message) {
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where(MigrationStatusFields.STATUS).in(ACTIVE_STATUSES),
            new Criteria().orOperator(
                Criteria.where(MigrationStatusFields.STARTED_AT).lt(staleBefore),
                Criteria.where(MigrationStatusFields.STARTED_AT).exists(false)
            )
        ));
        Update update = new Update()
            .set(MigrationStatusFields.STATUS, "failed")
            .set(MigrationStatusFields.COMPLETED_AT, now)
            .set(MigrationStatusFields.ERROR, message)
            .set(MigrationStatusFields.PROGRESS_MESSAGE, message)
            .set(MigrationStatusFields.COOLDOWN_EXEMPT, true);
        return updateMulti(server, query, update).getModifiedCount();
    }

    public void cancelMigration(Server server, String id, String error, Date completedAt,
                                String progressMessage, boolean cooldownExempt) {
        Update update = new Update()
            .set(MigrationStatusFields.STATUS, "failed")
            .set(MigrationStatusFields.ERROR, error)
            .set(MigrationStatusFields.COMPLETED_AT, completedAt)
            .set(MigrationStatusFields.PROGRESS_MESSAGE, progressMessage)
            .set(MigrationStatusFields.COOLDOWN_EXEMPT, cooldownExempt);
        updateFirst(server, Query.query(Criteria.where(MigrationStatusFields.ID).is(id)), update);
    }

    public void updateProgress(Server server, String id, String status, String message,
                                Integer recordsProcessed, Integer recordsSkipped,
                                Integer totalRecords, Date completedAt) {
        Update update = new Update()
            .set(MigrationStatusFields.STATUS, status)
            .set(MigrationStatusFields.PROGRESS_MESSAGE, message);

        if (recordsProcessed != null) {
            update.set(MigrationStatusFields.PROGRESS_RECORDS_PROCESSED, recordsProcessed);
        }
        if (recordsSkipped != null) {
            update.set(MigrationStatusFields.PROGRESS_RECORDS_SKIPPED, recordsSkipped);
        }
        if (totalRecords != null) {
            update.set(MigrationStatusFields.PROGRESS_TOTAL_RECORDS, totalRecords);
        }
        if (completedAt != null) {
            update.set(MigrationStatusFields.COMPLETED_AT, completedAt);
        }
        updateFirst(server, Query.query(Criteria.where(MigrationStatusFields.ID).is(id)), update);
    }
}
