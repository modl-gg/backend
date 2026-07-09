package gg.modl.backend.migration.service;

import gg.modl.backend.migration.config.MigrationConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MigrationUploadSweeper {
    private static final String UPLOAD_PREFIX = "migration-";
    private static final String UPLOAD_SUFFIX = ".json";

    private final MigrationConfiguration migrationConfiguration;
    private final Duration staleness;

    public MigrationUploadSweeper(
        MigrationConfiguration migrationConfiguration,
        @Value("${modl.migration.sweep.staleness:PT30M}") Duration staleness
    ) {
        this.migrationConfiguration = migrationConfiguration;
        this.staleness = staleness;
    }

    @Scheduled(fixedDelayString = "${modl.migration.sweep.interval:PT15M}")
    public void sweepStaleUploads() {
        Path uploadDir = Paths.get(migrationConfiguration.getUploadDir()).toAbsolutePath();
        if (!Files.isDirectory(uploadDir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(staleness);
        int deleted = 0;
        try (Stream<Path> files = Files.list(uploadDir)) {
            List<Path> stale = files.filter(file -> isStaleUpload(file, cutoff)).toList();
            for (Path file : stale) {
                deleted += deleteQuietly(file);
            }
        } catch (IOException e) {
            log.warn("Failed to sweep stale migration uploads in {}", uploadDir, e);
        }

        if (deleted > 0) {
            log.info("Swept {} stale migration upload(s) from {}", deleted, uploadDir);
        }
    }

    private boolean isStaleUpload(Path file, Instant cutoff) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file) || !name.startsWith(UPLOAD_PREFIX) || !name.endsWith(UPLOAD_SUFFIX)) {
            return false;
        }
        try {
            FileTime lastModified = Files.getLastModifiedTime(file);
            return lastModified.toInstant().isBefore(cutoff);
        } catch (IOException e) {
            log.warn("Failed to read modification time of migration upload {}", file, e);
            return false;
        }
    }

    private int deleteQuietly(Path file) {
        try {
            return Files.deleteIfExists(file) ? 1 : 0;
        } catch (IOException e) {
            log.warn("Failed to delete stale migration upload {}", file, e);
            return 0;
        }
    }
}
