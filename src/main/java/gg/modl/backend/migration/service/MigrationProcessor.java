package gg.modl.backend.migration.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.validation.MigrationValidator;
import gg.modl.backend.player.data.*;
import gg.modl.backend.player.data.punishment.*;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationProcessor {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final MigrationService migrationService;
    private final MigrationValidator validator;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 500;
    private static final int PROGRESS_UPDATE_INTERVAL = 1000;

    @Async
    public void processFileAsync(Server server, Path filePath) {
        try {
            processFile(server, filePath);
        } catch (Exception e) {
            log.error("Async migration processing failed", e);
        }
    }

    public void processFile(Server server, Path filePath) {
        int recordsProcessed = 0;
        int recordsSkipped = 0;

        try {
            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "processing_data",
                    "Reading and validating migration file...",
                    0, 0, null
            ));

            Map<String, Object> migrationData = objectMapper.readValue(filePath.toFile(), Map.class);

            MigrationValidator.ValidationResult validation = validator.validateMigrationData(migrationData);
            if (!validation.valid()) {
                migrationService.updateProgress(server, new UpdateProgressRequest(
                        "failed",
                        validation.error(),
                        0, 0, null
                ));
                return;
            }

            int totalRecords = validation.playerCount();
            List<?> players = (List<?>) migrationData.get("players");

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "processing_data",
                    "Processing " + totalRecords + " player records...",
                    0, 0, totalRecords
            ));

            MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
            List<Map<?, ?>> batch = new ArrayList<>();

            for (int i = 0; i < players.size(); i++) {
                Object playerObj = players.get(i);

                if (!(playerObj instanceof Map<?, ?>)) {
                    recordsSkipped++;
                    continue;
                }

                Map<?, ?> playerMap = (Map<?, ?>) playerObj;
                batch.add(playerMap);

                if (batch.size() >= BATCH_SIZE || i == players.size() - 1) {
                    int[] results = processBatch(template, batch);
                    recordsProcessed += results[0];
                    recordsSkipped += results[1];
                    batch.clear();

                    if (recordsProcessed % PROGRESS_UPDATE_INTERVAL == 0 || i == players.size() - 1) {
                        migrationService.updateProgress(server, new UpdateProgressRequest(
                                "processing_data",
                                "Processing player records... (" + recordsProcessed + "/" + totalRecords + ")",
                                recordsProcessed, recordsSkipped, totalRecords
                        ));
                    }
                }
            }

            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "completed",
                    "Migration completed successfully",
                    recordsProcessed, recordsSkipped, totalRecords
            ));

        } catch (Exception e) {
            log.error("Error processing migration file", e);
            migrationService.updateProgress(server, new UpdateProgressRequest(
                    "failed",
                    "Migration failed: " + e.getMessage(),
                    recordsProcessed, recordsSkipped, null
            ));
        } finally {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete migration file: {}", filePath, e);
            }
        }
    }

    private int[] processBatch(MongoTemplate template, List<Map<?, ?>> batch) {
        int processed = 0;
        int skipped = 0;

        List<String> uuids = new ArrayList<>();
        Map<String, Map<?, ?>> playerDataMap = new HashMap<>();

        for (Map<?, ?> playerMap : batch) {
            Object uuidObj = playerMap.get("minecraftUuid");
            if (uuidObj == null || !(uuidObj instanceof String)) {
                skipped++;
                continue;
            }

            String uuid = validator.normalizeUuid((String) uuidObj);
            if (!validator.isValidUuid(uuid)) {
                skipped++;
                continue;
            }

            uuids.add(uuid);
            playerDataMap.put(uuid, playerMap);
        }

        if (uuids.isEmpty()) {
            return new int[]{0, skipped};
        }

        Query existingQuery = Query.query(Criteria.where("minecraftUuid").in(
                uuids.stream().map(UUID::fromString).toList()
        ));
        List<Player> existingPlayers = template.find(existingQuery, Player.class, CollectionName.PLAYERS);
        Map<String, Player> existingMap = new HashMap<>();
        for (Player p : existingPlayers) {
            existingMap.put(p.getMinecraftUuid().toString(), p);
        }

        List<Player> toInsert = new ArrayList<>();
        BulkOperations bulkOps = template.bulkOps(BulkOperations.BulkMode.UNORDERED, Player.class, CollectionName.PLAYERS);
        boolean hasUpdates = false;

        for (String uuid : uuids) {
            try {
                Map<?, ?> playerMap = playerDataMap.get(uuid);
                Player existing = existingMap.get(uuid);

                if (existing != null) {
                    Update update = buildMergeUpdate(existing, playerMap);
                    if (update != null) {
                        bulkOps.updateOne(
                                Query.query(Criteria.where("minecraftUuid").is(UUID.fromString(uuid))),
                                update
                        );
                        hasUpdates = true;
                    }
                } else {
                    Player newPlayer = buildNewPlayer(uuid, playerMap);
                    if (newPlayer != null) {
                        toInsert.add(newPlayer);
                    }
                }
                processed++;
            } catch (Exception e) {
                log.warn("Error processing player {}: {}", uuid, e.getMessage());
                skipped++;
            }
        }

        if (!toInsert.isEmpty()) {
            template.insertAll(toInsert);
        }

        if (hasUpdates) {
            bulkOps.execute();
        }

        return new int[]{processed, skipped};
    }

    private Player buildNewPlayer(String uuid, Map<?, ?> data) {
        try {
            Player player = Player.builder()
                    .id(UUID.randomUUID().toString())
                    .minecraftUuid(UUID.fromString(uuid))
                    .usernames(parseUsernames(data.get("usernames")))
                    .notes(parseNotes(data.get("notes")))
                    .ipAddresses(parseIpList(data.get("ipList")))
                    .punishments(parsePunishments(data.get("punishments")))
                    .data(parseData(data.get("data")))
                    .build();

            return player;
        } catch (Exception e) {
            log.warn("Error building new player for UUID {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    private Update buildMergeUpdate(Player existing, Map<?, ?> newData) {
        Update update = new Update();
        boolean hasChanges = false;

        List<UsernameEntry> newUsernames = parseUsernames(newData.get("usernames"));
        if (!newUsernames.isEmpty()) {
            Set<String> existingNames = new HashSet<>();
            for (UsernameEntry u : existing.getUsernames()) {
                existingNames.add(u.username());
            }
            for (UsernameEntry u : newUsernames) {
                if (!existingNames.contains(u.username())) {
                    update.push("usernames", u);
                    hasChanges = true;
                }
            }
        }

        List<NoteEntry> newNotes = parseNotes(newData.get("notes"));
        if (!newNotes.isEmpty()) {
            for (NoteEntry note : newNotes) {
                update.push("notes", note);
                hasChanges = true;
            }
        }

        List<Punishment> newPunishments = parsePunishments(newData.get("punishments"));
        if (!newPunishments.isEmpty()) {
            Set<String> existingIds = new HashSet<>();
            for (Punishment p : existing.getPunishments()) {
                existingIds.add(p.getId());
            }
            for (Punishment p : newPunishments) {
                if (!existingIds.contains(p.getId())) {
                    update.push("punishments", p);
                    hasChanges = true;
                }
            }
        }

        return hasChanges ? update : null;
    }

    private List<UsernameEntry> parseUsernames(Object data) {
        List<UsernameEntry> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;

            String username = validator.sanitizeString((String) map.get("username"), 100);
            Date date = validator.parseDate(map.get("date"));

            if (username != null && !username.isBlank() && date != null) {
                result.add(new UsernameEntry(username, date));
            }
        }

        return result;
    }

    private List<NoteEntry> parseNotes(Object data) {
        List<NoteEntry> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;

            String text = validator.sanitizeString((String) map.get("text"), 5000);
            Date date = validator.parseDate(map.get("date"));
            String issuerName = validator.sanitizeString((String) map.get("issuerName"), 100);

            if (text != null && date != null && issuerName != null) {
                result.add(new NoteEntry(
                        UUID.randomUUID().toString(),
                        text,
                        date,
                        issuerName,
                        null
                ));
            }
        }

        return result;
    }

    private List<IPEntry> parseIpList(Object data) {
        List<IPEntry> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;

            String ipAddress = (String) map.get("ipAddress");
            if (!validator.isValidIpAddress(ipAddress)) continue;

            Date firstLogin = validator.parseDate(map.get("firstLogin"));
            if (firstLogin == null) {
                firstLogin = new Date();
            }

            List<Date> logins = new ArrayList<>();
            Object loginsObj = map.get("logins");
            if (loginsObj instanceof List<?>) {
                for (Object loginObj : (List<?>) loginsObj) {
                    Date login = validator.parseDate(loginObj);
                    if (login != null) {
                        logins.add(login);
                    }
                }
            }

            result.add(IPEntry.builder()
                    .ipAddress(ipAddress)
                    .country(validator.sanitizeString((String) map.get("country"), 100))
                    .region(validator.sanitizeString((String) map.get("region"), 100))
                    .asn(validator.sanitizeString((String) map.get("asn"), 100))
                    .proxy(Boolean.TRUE.equals(map.get("proxy")))
                    .hosting(Boolean.TRUE.equals(map.get("hosting")))
                    .firstLogin(firstLogin)
                    .logins(logins)
                    .build());
        }

        return result;
    }

    private List<Punishment> parsePunishments(Object data) {
        List<Punishment> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) continue;
            Map<?, ?> map = (Map<?, ?>) item;

            String id = (String) map.get("_id");
            if (id == null) {
                id = UUID.randomUUID().toString();
            }

            Date issued = validator.parseDate(map.get("issued"));
            if (issued == null) {
                continue;
            }

            String issuerName = validator.sanitizeString((String) map.get("issuerName"), 100);
            if (issuerName == null) {
                issuerName = "Unknown";
            }

            Object typeOrdinalObj = map.get("type_ordinal");
            int typeOrdinal = 0;
            if (typeOrdinalObj instanceof Number) {
                typeOrdinal = ((Number) typeOrdinalObj).intValue();
            }

            List<PunishmentNote> notes = new ArrayList<>();
            Object notesObj = map.get("notes");
            if (notesObj instanceof List<?>) {
                for (Object noteObj : (List<?>) notesObj) {
                    if (noteObj instanceof Map<?, ?>) {
                        Map<?, ?> noteMap = (Map<?, ?>) noteObj;
                        String text = validator.sanitizeString((String) noteMap.get("text"), 5000);
                        Date date = validator.parseDate(noteMap.get("date"));
                        String noteIssuer = validator.sanitizeString((String) noteMap.get("issuerName"), 100);

                        if (text != null && date != null) {
                            notes.add(new PunishmentNote(text, date, noteIssuer != null ? noteIssuer : "Unknown"));
                        }
                    }
                }
            }

            List<PunishmentEvidence> evidence = new ArrayList<>();

            List<PunishmentModification> modifications = new ArrayList<>();

            List<String> attachedTicketIds = new ArrayList<>();
            Object ticketIdsObj = map.get("attachedTicketIds");
            if (ticketIdsObj instanceof List<?>) {
                for (Object ticketId : (List<?>) ticketIdsObj) {
                    if (ticketId instanceof String) {
                        attachedTicketIds.add((String) ticketId);
                    }
                }
            }

            Map<String, Object> punishmentData = new HashMap<>();
            Object dataObj = map.get("data");
            if (dataObj instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) dataObj).entrySet()) {
                    if (entry.getKey() instanceof String) {
                        punishmentData.put((String) entry.getKey(), entry.getValue());
                    }
                }
            }

            String reason = validator.sanitizeString((String) map.get("reason"), 1000);
            if (reason != null && !reason.isBlank()) {
                punishmentData.put("reason", reason);
            }

            Object durationObj = map.get("duration");
            if (durationObj instanceof Number) {
                punishmentData.put("duration", ((Number) durationObj).longValue());
            }

            Date started = validator.parseDate(map.get("started"));

            Punishment punishment = new Punishment(
                    id,
                    typeOrdinal,
                    issuerName,
                    issued,
                    modifications,
                    notes,
                    evidence,
                    attachedTicketIds,
                    punishmentData.isEmpty() ? null : punishmentData
            );

            if (started != null) {
                punishment.setStarted(started);
            }

            result.add(punishment);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(Object data) {
        if (data instanceof Map<?, ?>) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) data).entrySet()) {
                if (entry.getKey() instanceof String) {
                    result.put((String) entry.getKey(), entry.getValue());
                }
            }
            return result;
        }
        return new HashMap<>();
    }
}
