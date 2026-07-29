package gg.modl.backend.migration.service;

import gg.modl.backend.database.mongo.repository.MigrationMongoRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.migration.data.MigrationStatus;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.SyncMigrationTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import gg.modl.backend.migration.config.MigrationConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {
    private final MigrationMongoRepository migrationRepository;
    private final MigrationConfiguration migrationConfiguration;
    private final RealtimeEventPublisher publisher;
    private final ServerLimitPolicy serverLimitPolicy;

    public record CooldownState(boolean onCooldown, @Nullable Long remainingTime) {}

    public record MigrationOperationResult(boolean success, @Nullable String taskId,
                                           @Nullable String message, @Nullable String error) {
        static MigrationOperationResult failure(String error) {
            return new MigrationOperationResult(false, null, null, error);
        }

        static MigrationOperationResult started(String taskId, String message) {
            return new MigrationOperationResult(true, taskId, message, null);
        }

        static MigrationOperationResult succeeded(String message) {
            return new MigrationOperationResult(true, null, message, null);
        }
    }

    public record FileSizeError(String error, String message, long fileSize, long limit) {}

    private static final List<String> VALID_TYPES = List.of("litebans");
    private static final List<String> VALID_STATUSES = List.of(
        "idle", "building_json", "uploading_json", "processing_data", "completed", "failed"
    );
    private static final long COOLDOWN_MS = 60 * 60 * 1000;
    private static final long STALE_MIGRATION_MS = 30 * 60 * 1000;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    public Optional<MigrationStatus> getLatestMigration(Server server) {
        Date now = new Date();
        Date staleBefore = new Date(now.getTime() - STALE_MIGRATION_MS);
        migrationRepository.failStaleMigrations(server, staleBefore, now,
            "Migration timed out and was automatically cancelled.");
        return migrationRepository.findLatest(server);
    }

    public boolean isActiveMigrationPresent(Server server) {
        return migrationRepository.existsActiveMigration(server);
    }

    public CooldownState checkCooldown(Server server) {
        MigrationStatus lastMigration = migrationRepository.findLatestCompletedOrFailed(server).orElse(null);

        if (lastMigration == null || lastMigration.getCompletedAt() == null) {
            return new CooldownState(false, null);
        }

        long timeSinceCompletion = System.currentTimeMillis() - lastMigration.getCompletedAt().getTime();

        if (timeSinceCompletion < COOLDOWN_MS) {
            return new CooldownState(true, COOLDOWN_MS - timeSinceCompletion);
        }

        return new CooldownState(false, null);
    }

    public MigrationOperationResult startMigration(Server server, String migrationType) {
        if (!VALID_TYPES.contains(migrationType.toLowerCase())) {
            return MigrationOperationResult.failure("Invalid migration type");
        }

        Date now = new Date();
        Date staleBefore = new Date(now.getTime() - STALE_MIGRATION_MS);
        migrationRepository.failStaleMigrations(server, staleBefore, now,
            "Migration timed out and was automatically cancelled.");

        if (migrationRepository.existsActiveMigration(server, staleBefore)) {
            return MigrationOperationResult.failure("A migration is already in progress");
        }

        CooldownState cooldown = checkCooldown(server);
        if (cooldown.onCooldown()) {
            return MigrationOperationResult.failure(
                "Migration on cooldown. Please wait before starting another migration.");
        }

        String taskId = UUID.randomUUID().toString();
        String type = migrationType.toLowerCase();

        MigrationStatus status = MigrationStatus.builder()
            .taskId(taskId)
            .type(type)
            .status("building_json")
            .progress(MigrationStatus.MigrationProgress.builder()
                .message("Waiting for Minecraft server to build migration file...")
                .recordsProcessed(0)
                .recordsSkipped(0)
                .build())
            .startedAt(now)
            .build();

        migrationRepository.saveEntity(server, status);

        publisher.pushMigrationTask(server, SyncMigrationTask.newBuilder()
            .setTaskId(taskId)
            .setType(type)
            .build());

        return MigrationOperationResult.started(taskId,
            "Migration task initiated. Waiting for Minecraft server to process.");
    }

    public MigrationOperationResult cancelMigration(Server server) {
        MigrationStatus activeMigration = migrationRepository.findActiveMigration(server).orElse(null);

        if (activeMigration == null) {
            return MigrationOperationResult.failure("No active migration to cancel");
        }

        boolean cooldownExempt = "building_json".equals(activeMigration.getStatus());

        migrationRepository.cancelMigration(server, activeMigration.getId(),
            "Cancelled by administrator", new Date(), "Migration cancelled by administrator", cooldownExempt);

        return MigrationOperationResult.succeeded("Migration cancelled successfully");
    }

    public Optional<FileSizeError> validateFileSize(Server server, MultipartFile file) {
        long fileSizeLimit = getFileSizeLimit(server);
        if (file.getSize() <= fileSizeLimit) {
            return Optional.empty();
        }

        double fileSizeMB = file.getSize() / (1024.0 * 1024.0);
        double limitMB = fileSizeLimit / (1024.0 * 1024.0);

        updateProgress(server, new UpdateProgressRequest(
            "failed", "Migration file exceeds size limit", 0, 0, null
        ));

        return Optional.of(new FileSizeError(
            "Migration file exceeds size limit",
            String.format("File size (%.2fMB) exceeds the limit of %.2fMB.", fileSizeMB, limitMB),
            file.getSize(),
            fileSizeLimit));
    }

    public void updateProgress(Server server, UpdateProgressRequest request) {
        if (request.status() == null || !VALID_STATUSES.contains(request.status())) {
            throw new ValidationException("Invalid status value");
        }

        if (request.message() == null) {
            throw new ValidationException("Message is required");
        }

        if (request.recordsProcessed() != null && request.recordsProcessed() < 0) {
            throw new ValidationException("Invalid recordsProcessed value");
        }

        if (request.totalRecords() != null && request.totalRecords() < 0) {
            throw new ValidationException("Invalid totalRecords value");
        }

        String message = clampMessage(request.message());

        MigrationStatus activeMigration = migrationRepository.findActiveMigration(server).orElse(null);

        if (activeMigration == null) {
            throw new ResourceNotFoundException("No active migration found");
        }

        Date completedAt = ("completed".equals(request.status()) || "failed".equals(request.status()))
            ? new Date() : null;

        migrationRepository.updateProgress(server, activeMigration.getId(),
            request.status(), message,
            request.recordsProcessed(), request.recordsSkipped(),
            request.totalRecords(), completedAt);
    }

    private static String clampMessage(String message) {
        return message == null ? null : MigrationMessages.truncate(message, MAX_MESSAGE_LENGTH);
    }

    public long getFileSizeLimit(Server server) {
        return serverLimitPolicy.resolve(server).getMigrationFileSizeLimit();
    }

    public Path saveUploadedFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(migrationConfiguration.getUploadDir()).toAbsolutePath();
            Files.createDirectories(uploadPath);
            String uniqueFilename = "migration-" + UUID.randomUUID() + ".json";
            Path filePath = uploadPath.resolve(uniqueFilename);
            file.transferTo(filePath.toAbsolutePath());
            return filePath;
        } catch (IOException e) {
            throw new ExternalServiceException("Failed to save migration file", e);
        }
    }

    public void requireActiveMigrationForUpload(Server server) {
        if (!migrationRepository.existsActiveMigration(server)) {
            throw new ResourceNotFoundException("No active migration found for upload");
        }
    }

    public void discardUpload(Server server, Path filePath, String reason) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete orphaned migration upload {}", filePath, e);
        }
        try {
            updateProgress(server, new UpdateProgressRequest("failed", reason, 0, 0, null));
        } catch (Exception e) {
            log.warn("Failed to mark migration failed after discarding upload {}", filePath, e);
        }
    }
}
