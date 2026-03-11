package gg.modl.backend.migration.service;

import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.migration.dto.MigrationStatusResponse;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {
    private final MigrationMongoRepository migrationRepository;

    private static final List<String> VALID_TYPES = List.of("litebans");
    private static final List<String> VALID_STATUSES = List.of(
            "idle", "building_json", "uploading_json", "processing_data", "completed", "failed"
    );
    private static final long COOLDOWN_MS = 60 * 60 * 1000;
    private static final long DEFAULT_FILE_SIZE_LIMIT = 500 * 1024 * 1024;

    public MigrationStatusResponse getMigrationStatus(Server server) {
        MigrationStatus status = migrationRepository.findLatest(server).orElse(null);

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
        MigrationStatus lastMigration = migrationRepository.findLatestCompletedOrFailed(server).orElse(null);

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

        if (migrationRepository.existsActiveMigration(server)) {
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

        migrationRepository.saveEntity(server, status);

        return Map.of(
                "success", true,
                "taskId", taskId,
                "message", "Migration task initiated. Waiting for Minecraft server to process."
        );
    }

    public Map<String, Object> cancelMigration(Server server) {
        MigrationStatus activeMigration = migrationRepository.findActiveMigration(server).orElse(null);

        if (activeMigration == null) {
            return Map.of("success", false, "error", "No active migration to cancel");
        }

        Update update = new Update()
                .set("status", "failed")
                .set("error", "Cancelled by administrator")
                .set("completedAt", new Date())
                .set("progress.message", "Migration cancelled by administrator");

        migrationRepository.updateById(server, activeMigration.getId(), update);

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

        MigrationStatus activeMigration = migrationRepository.findActiveMigration(server).orElse(null);

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

        migrationRepository.updateById(server, activeMigration.getId(), update);

        return Map.of("success", true);
    }

    public long getFileSizeLimit(Server server) {
        if (server.getMigrationFileSizeLimit() != null) {
            return server.getMigrationFileSizeLimit();
        }
        return DEFAULT_FILE_SIZE_LIMIT;
    }
}
