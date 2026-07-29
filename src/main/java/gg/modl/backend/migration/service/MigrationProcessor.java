package gg.modl.backend.migration.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.validation.MigrationValidator;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationProcessor {
    private final PlayerMongoRepository playerRepository;
    private final MigrationService migrationService;
    private final MigrationValidator validator;
    private final ObjectMapper objectMapper;
    private final MigrationRecordMapper recordMapper;

    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_UPDATE_INTERVAL = 1000;
    private static final int MAX_JSON_NESTING_DEPTH = 100;
    private static final int MAX_JSON_STRING_LENGTH = 1_000_000;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 900;
    private static final String PLAYERS_FIELD = "players";
    private static final String METADATA_FIELD = "metadata";
    private static final String PLAYER_COUNT_FIELD = "playerCount";

    @Async("migrationTaskExecutor")
    public void processFileAsync(Server server, Path filePath) {
        try {
            processFile(server, filePath);
        } catch (Exception e) {
            log.error("Async migration processing failed", e);
        }
    }

    public void processFile(Server server, Path filePath) {
        ProgressCounters counters = new ProgressCounters();

        try {
            migrationService.updateProgress(server, new UpdateProgressRequest(
                "processing_data",
                "Reading and validating migration file...",
                0, 0, null
            ));

            ObjectMapper constrainedMapper = objectMapper.copy();
            constrainedMapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(MAX_JSON_NESTING_DEPTH)
                .maxStringLength(MAX_JSON_STRING_LENGTH)
                .build());

            if (streamMigrationFile(server, filePath, constrainedMapper, counters)) {
                return;
            }

            try {
                migrationService.updateProgress(server, new UpdateProgressRequest(
                    "completed",
                    "Migration completed successfully",
                    counters.processed(), counters.skipped(), counters.total()
                ));
            } catch (Exception e) {
                log.error("Failed to persist terminal migration state (completed)", e);
            }

        } catch (MigrationDataException e) {
            failMigration(server, e.getMessage(), counters);
        } catch (Exception e) {
            log.error("Error processing migration file", e);
            failMigration(server, boundFailureMessage("Migration failed: ", e.getMessage()), counters);
        } finally {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete migration file: {}", filePath, e);
            }
        }
    }

    private void failMigration(Server server, String message, ProgressCounters counters) {
        try {
            migrationService.updateProgress(server, new UpdateProgressRequest(
                "failed", message, counters.processed(), counters.skipped(), null
            ));
        } catch (Exception e) {
            log.error("Failed to persist terminal migration state (failed)", e);
        }
    }

    private boolean streamMigrationFile(Server server, Path filePath, ObjectMapper mapper,
                                        ProgressCounters counters) throws IOException {
        JsonFactory factory = mapper.getFactory();
        try (JsonParser parser = factory.createParser(filePath.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new MigrationDataException("Migration data must be a JSON object");
            }

            boolean playersStreamed = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();

                if (PLAYERS_FIELD.equals(field)) {
                    MigrationValidator.ValidationResult header = validator.validateHeader(
                        true, parser.currentToken() == JsonToken.START_ARRAY, counters.total());
                    if (!header.valid()) {
                        throw new MigrationDataException(header.error());
                    }
                    announceProcessingProgress(server, counters);
                    if (streamPlayers(server, parser, mapper, counters)) {
                        return true;
                    }
                    playersStreamed = true;
                } else if (METADATA_FIELD.equals(field)) {
                    counters.total(readDeclaredPlayerCount(parser));
                } else {
                    parser.skipChildren();
                }
            }

            if (!playersStreamed) {
                throw new MigrationDataException(
                    validator.validateHeader(false, false, counters.total()).error());
            }

            return false;
        }
    }

    private boolean streamPlayers(Server server, JsonParser parser, ObjectMapper mapper,
                                  ProgressCounters counters) throws IOException {
        long seen = 0;
        List<Map<?, ?>> batch = new ArrayList<>(BATCH_SIZE);

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            seen++;
            if (seen > MigrationValidator.MAX_PLAYER_RECORDS) {
                throw new MigrationDataException("Players array exceeds maximum length of 1,000,000");
            }

            if (parser.currentToken() == JsonToken.START_OBJECT) {
                batch.add(mapper.readValue(parser, Map.class));
            } else {
                parser.skipChildren();
                counters.skipOne();
            }

            if (batch.size() >= BATCH_SIZE && drainBatch(server, batch, counters)) {
                return true;
            }
        }

        if (seen == 0) {
            throw new MigrationDataException("Players array cannot be empty");
        }

        return !batch.isEmpty() && drainBatch(server, batch, counters);
    }

    private boolean drainBatch(Server server, List<Map<?, ?>> batch, ProgressCounters counters) {
        BatchResult results = processBatch(server, batch);
        counters.addProcessed(results.processed());
        counters.addSkipped(results.skipped());
        batch.clear();

        if (counters.dueForAnnounce(PROGRESS_UPDATE_INTERVAL)) {
            announceProcessingProgress(server, counters);
        }

        if (!migrationService.isActiveMigrationPresent(server)) {
            log.info("Migration cancelled or no longer active; stopping processing after {} records",
                counters.processed());
            return true;
        }
        return false;
    }

    private void announceProcessingProgress(Server server, ProgressCounters counters) {
        migrationService.updateProgress(server, new UpdateProgressRequest(
            "processing_data",
            processingMessage(counters.processed(), counters.total()),
            counters.processed(), counters.skipped(), counters.total()
        ));
    }

    private Integer readDeclaredPlayerCount(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return null;
        }
        Integer playerCount = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            if (PLAYER_COUNT_FIELD.equals(field) && parser.currentToken().isNumeric()) {
                long value = parser.getValueAsLong(-1L);
                playerCount = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
            } else {
                parser.skipChildren();
            }
        }
        return playerCount;
    }

    private static String processingMessage(int processed, Integer total) {
        if (total == null) {
            return "Processing player records... (" + processed + ")";
        }
        return "Processing player records... (" + processed + "/" + total + ")";
    }

    private static String boundFailureMessage(String prefix, String detail) {
        return MigrationMessages.truncate(prefix + (detail == null ? "unknown error" : detail),
            MAX_FAILURE_MESSAGE_LENGTH);
    }

    private BatchResult processBatch(Server server, List<Map<?, ?>> batch) {
        int processed = 0;
        int skipped = 0;

        List<String> uuids = new ArrayList<>();
        Map<String, Map<?, ?>> playerDataMap = new HashMap<>();

        for (Map<?, ?> playerMap : batch) {
            Object uuidObj = playerMap.get("minecraftUuid");
            if (!(uuidObj instanceof String uuidStr)) {
                skipped++;
                continue;
            }

            String uuid = validator.normalizeUuid(uuidStr);
            if (!validator.isValidUuid(uuid)) {
                skipped++;
                continue;
            }

            uuids.add(uuid);
            playerDataMap.put(uuid, playerMap);
        }

        if (uuids.isEmpty()) {
            return new BatchResult(0, skipped);
        }

        List<Player> existingPlayers = playerRepository.findByMinecraftUuids(server,
            uuids.stream().map(UUID::fromString).toList());
        Map<String, Player> existingMap = new HashMap<>();
        for (Player p : existingPlayers) {
            existingMap.put(p.getMinecraftUuid().toString(), p);
        }

        List<Player> toInsert = new ArrayList<>();
        Map<UUID, Update> mergeUpdates = new HashMap<>();

        for (String uuid : uuids) {
            try {
                Map<?, ?> playerMap = playerDataMap.get(uuid);
                Player existing = existingMap.get(uuid);

                if (existing != null) {
                    Update update = recordMapper.buildMergeUpdate(existing, playerMap);
                    if (update != null) {
                        mergeUpdates.put(UUID.fromString(uuid), update);
                    }
                } else {
                    Player newPlayer = recordMapper.buildNewPlayer(uuid, playerMap);
                    if (newPlayer != null) {
                        toInsert.add(newPlayer);
                    }
                }
                processed++;
            } catch (Exception e) {
                log.warn("Error processing player {}", uuid, e);
                skipped++;
            }
        }

        if (!toInsert.isEmpty()) {
            playerRepository.insertAll(server, toInsert);
        }

        if (!mergeUpdates.isEmpty()) {
            playerRepository.bulkMergeByUuid(server, mergeUpdates);
        }

        return new BatchResult(processed, skipped);
    }

    private record BatchResult(int processed, int skipped) {}

    private static final class ProgressCounters {
        private int processed;
        private int skipped;
        private int lastAnnounced;
        private Integer total;

        private boolean dueForAnnounce(int interval) {
            if (processed - lastAnnounced >= interval) {
                lastAnnounced = processed;
                return true;
            }
            return false;
        }

        private void addProcessed(int value) {
            processed += value;
        }

        private void addSkipped(int value) {
            skipped += value;
        }

        private void skipOne() {
            skipped++;
        }

        private int processed() {
            return processed;
        }

        private int skipped() {
            return skipped;
        }

        private Integer total() {
            return total;
        }

        private void total(Integer value) {
            total = value;
        }
    }

    private static final class MigrationDataException extends RuntimeException {
        private MigrationDataException(String message) {
            super(message);
        }
    }
}
