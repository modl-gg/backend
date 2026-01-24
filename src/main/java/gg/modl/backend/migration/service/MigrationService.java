package gg.modl.backend.migration.service;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.migration.dto.MigrationStatusResponse;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {
    private final DynamicMongoTemplateProvider mongoProvider;

    private static final String COLLECTION_NAME = "migrations";
    private static final List<String> VALID_TYPES = List.of("litebans");
    private static final List<String> VALID_STATUSES = List.of(
            "idle", "building_json", "uploading_json", "processing_data", "completed", "failed"
    );
    private static final long COOLDOWN_MS = 60 * 60 * 1000; // 1 hour
    private static final long DEFAULT_FILE_SIZE_LIMIT = 500 * 1024 * 1024; // 500 MB

    public MigrationStatusResponse getMigrationStatus(Server server) {
        MongoTemplate template = getTemplate(server);

        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "startedAt"))
                .limit(1);

        MigrationStatus status = template.findOne(query, MigrationStatus.class, COLLECTION_NAME);

        MigrationStatusResponse.CurrentMigration currentMigration = null;
        if (status != null) {
            currentMigration = new MigrationStatusResponse.CurrentMigration(
                    status.getTaskId(),
                    status.getType(),
                    status.getStatus(),
                    status.getProgress(),
                    status.getStartedAt(),
                    status.getCompletedAt(),
                    status.getError()
            );
        }

        MigrationStatusResponse.CooldownInfo cooldown = checkCooldown(server);

        return new MigrationStatusResponse(currentMigration, cooldown);
    }

    public MigrationStatusResponse.CooldownInfo checkCooldown(Server server) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(
                Criteria.where("status").in("completed", "failed")
        ).with(Sort.by(Sort.Direction.DESC, "completedAt")).limit(1);

        MigrationStatus lastMigration = template.findOne(query, MigrationStatus.class, COLLECTION_NAME);

        if (lastMigration == null || lastMigration.getCompletedAt() == null) {
            return new MigrationStatusResponse.CooldownInfo(false, null);
        }

        long timeSinceCompletion = System.currentTimeMillis() - lastMigration.getCompletedAt().getTime();

        if (timeSinceCompletion < COOLDOWN_MS) {
            return new MigrationStatusResponse.CooldownInfo(true, COOLDOWN_MS - timeSinceCompletion);
        }

        return new MigrationStatusResponse.CooldownInfo(false, null);
    }

    public Map<String, Object> startMigration(Server server, String migrationType) {
        if (!VALID_TYPES.contains(migrationType.toLowerCase())) {
            return Map.of("success", false, "error", "Invalid migration type");
        }

        MongoTemplate template = getTemplate(server);

        Query activeQuery = Query.query(
                Criteria.where("status").in("building_json", "uploading_json", "processing_data")
        );
        if (template.exists(activeQuery, MigrationStatus.class, COLLECTION_NAME)) {
            return Map.of("success", false, "error", "A migration is already in progress");
        }

        MigrationStatusResponse.CooldownInfo cooldown = checkCooldown(server);
        if (cooldown.onCooldown()) {
            return Map.of("success", false, "error", "Migration on cooldown. Please wait before starting another migration.");
        }

        String taskId = UUID.randomUUID().toString();
        Date now = new Date();

        MigrationStatus status = MigrationStatus.builder()
                .taskId(taskId)
                .type(migrationType.toLowerCase())
                .status("building_json")
                .progress(MigrationStatus.MigrationProgress.builder()
                        .message("Waiting for Minecraft server to build migration file...")
                        .recordsProcessed(0)
                        .recordsSkipped(0)
                        .build())
                .startedAt(now)
                .build();

        template.save(status, COLLECTION_NAME);

        return Map.of(
                "success", true,
                "taskId", taskId,
                "message", "Migration task initiated. Waiting for Minecraft server to process."
        );
    }

    public Map<String, Object> cancelMigration(Server server) {
        MongoTemplate template = getTemplate(server);

        Query activeQuery = Query.query(
                Criteria.where("status").in("building_json", "uploading_json", "processing_data")
        );
        MigrationStatus activeMigration = template.findOne(activeQuery, MigrationStatus.class, COLLECTION_NAME);

        if (activeMigration == null) {
            return Map.of("success", false, "error", "No active migration to cancel");
        }

        Update update = new Update()
                .set("status", "failed")
                .set("error", "Cancelled by administrator")
                .set("completedAt", new Date())
                .set("progress.message", "Migration cancelled by administrator");

        template.updateFirst(
                Query.query(Criteria.where("_id").is(activeMigration.getId())),
                update,
                MigrationStatus.class,
                COLLECTION_NAME
        );

        return Map.of("success", true, "message", "Migration cancelled successfully");
    }

    private static final int MAX_MESSAGE_LENGTH = 1000;

    public Map<String, Object> updateProgress(Server server, UpdateProgressRequest request) {
        if (request.status() == null || !VALID_STATUSES.contains(request.status())) {
            return Map.of("success", false, "error", "Invalid status value");
        }

        if (request.message() == null || request.message().length() > MAX_MESSAGE_LENGTH) {
            return Map.of("success", false, "error", "Invalid or too long message");
        }

        if (request.recordsProcessed() != null && request.recordsProcessed() < 0) {
            return Map.of("success", false, "error", "Invalid recordsProcessed value");
        }

        if (request.totalRecords() != null && request.totalRecords() < 0) {
            return Map.of("success", false, "error", "Invalid totalRecords value");
        }

        MongoTemplate template = getTemplate(server);

        Query activeQuery = Query.query(
                Criteria.where("status").in("building_json", "uploading_json", "processing_data")
        );
        MigrationStatus activeMigration = template.findOne(activeQuery, MigrationStatus.class, COLLECTION_NAME);

        if (activeMigration == null) {
            return Map.of("success", false, "error", "No active migration found");
        }

        Update update = new Update()
                .set("status", request.status())
                .set("progress.message", request.message());

        if (request.recordsProcessed() != null) {
            update.set("progress.recordsProcessed", request.recordsProcessed());
        }
        if (request.recordsSkipped() != null) {
            update.set("progress.recordsSkipped", request.recordsSkipped());
        }
        if (request.totalRecords() != null) {
            update.set("progress.totalRecords", request.totalRecords());
        }

        if ("completed".equals(request.status()) || "failed".equals(request.status())) {
            update.set("completedAt", new Date());
        }

        template.updateFirst(
                Query.query(Criteria.where("_id").is(activeMigration.getId())),
                update,
                MigrationStatus.class,
                COLLECTION_NAME
        );

        return Map.of("success", true);
    }

    public long getFileSizeLimit(Server server) {
        if (server.getMigrationFileSizeLimit() != null) {
            return server.getMigrationFileSizeLimit();
        }
        return DEFAULT_FILE_SIZE_LIMIT;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
