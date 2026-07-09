package gg.modl.backend.migration.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.migration.dto.UpdateProgressRequest;
import gg.modl.backend.migration.validation.MigrationValidator;
import gg.modl.backend.player.PlayerDocumentIdGenerator;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.server.data.Server;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.infrastructure.validation.SafeUrls;
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
        int[] results = processBatch(server, batch);
        counters.addProcessed(results[0]);
        counters.addSkipped(results[1]);
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
        String message = prefix + (detail == null ? "unknown error" : detail);
        if (message.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            return message.substring(0, MAX_FAILURE_MESSAGE_LENGTH - 1) + "…";
        }
        return message;
    }

    private int[] processBatch(Server server, List<Map<?, ?>> batch) {
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
                    Update update = buildMergeUpdate(existing, playerMap);
                    if (update != null) {
                        mergeUpdates.put(UUID.fromString(uuid), update);
                    }
                } else {
                    Player newPlayer = buildNewPlayer(uuid, playerMap);
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

        return new int[]{processed, skipped};
    }

    private Player buildNewPlayer(String uuid, Map<?, ?> data) {
        try {
            Object ipObj = data.get("ipAddresses") != null ? data.get("ipAddresses") : data.get("ipList");
            Player player = Player.builder()
                .id(PlayerDocumentIdGenerator.generate())
                .minecraftUuid(UUID.fromString(uuid))
                .usernames(parseUsernames(data.get("usernames")))
                .notes(parseNotes(data.get("notes")))
                .ipAddresses(parseIpAddresses(ipObj))
                .punishments(parsePunishments(data.get("punishments")))
                .data(parseData(data.get("data")))
                .build();

            return player;
        } catch (Exception e) {
            log.warn("Error building new player for UUID {}", uuid, e);
            return null;
        }
    }

    private List<IPEntry> parseIpAddresses(Object data) {
        List<IPEntry> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;

            String ipAddress = (String) map.get("ipAddress");
            if (!validator.isValidIpAddress(ipAddress)) {
                continue;
            }

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

    private Update buildMergeUpdate(Player existing, Map<?, ?> newData) {
        Update update = new Update();
        boolean hasChanges = false;

        List<UsernameEntry> newUsernames = parseUsernames(newData.get("usernames"));
        if (!newUsernames.isEmpty()) {
            Set<String> existingNames = new HashSet<>();
            if (existing.getUsernames() != null) {
                for (UsernameEntry u : existing.getUsernames()) {
                    existingNames.add(u.username());
                }
            }
            List<UsernameEntry> toAddUsernames = new ArrayList<>();
            for (UsernameEntry u : newUsernames) {
                if (existingNames.add(u.username())) {
                    toAddUsernames.add(u);
                }
            }
            if (!toAddUsernames.isEmpty()) {
                update.push("usernames").each(toAddUsernames.toArray());
                hasChanges = true;
            }
        }

        List<NoteEntry> newNotes = parseNotes(newData.get("notes"));
        if (!newNotes.isEmpty()) {
            Set<String> existingNoteIds = new HashSet<>();
            if (existing.getNotes() != null) {
                for (NoteEntry note : existing.getNotes()) {
                    existingNoteIds.add(note.getId());
                }
            }
            List<NoteEntry> toAddNotes = new ArrayList<>();
            for (NoteEntry note : newNotes) {
                if (existingNoteIds.add(note.getId())) {
                    toAddNotes.add(note);
                }
            }
            if (!toAddNotes.isEmpty()) {
                update.push("notes").each(toAddNotes.toArray());
                hasChanges = true;
            }
        }

        List<Punishment> newPunishments = parsePunishments(newData.get("punishments"));
        if (!newPunishments.isEmpty()) {
            Set<String> existingIds = new HashSet<>();
            if (existing.getPunishments() != null) {
                for (Punishment p : existing.getPunishments()) {
                    existingIds.add(p.getId());
                }
            }
            List<Punishment> toAddPunishments = new ArrayList<>();
            for (Punishment p : newPunishments) {
                if (existingIds.add(p.getId())) {
                    toAddPunishments.add(p);
                }
            }
            if (!toAddPunishments.isEmpty()) {
                update.push("punishments").each(toAddPunishments.toArray());
                hasChanges = true;
            }
        }

        Object ipObj = newData.get("ipAddresses") != null ? newData.get("ipAddresses") : newData.get("ipList");
        List<IPEntry> newIps = parseIpAddresses(ipObj);
        if (!newIps.isEmpty()) {
            Set<String> existingIps = new HashSet<>();
            if (existing.getIpAddresses() != null) {
                for (IPEntry ip : existing.getIpAddresses()) {
                    if (ip.getIpAddress() != null) {
                        existingIps.add(ip.getIpAddress());
                    }
                }
            }
            List<IPEntry> ipsToAdd = new ArrayList<>();
            for (IPEntry ip : newIps) {
                if (ip.getIpAddress() != null && existingIps.add(ip.getIpAddress())) {
                    ipsToAdd.add(ip);
                }
            }
            if (!ipsToAdd.isEmpty()) {
                update.push("ipAddresses").each(ipsToAdd.toArray());
                hasChanges = true;
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
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
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
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;

            String text = validator.sanitizeString((String) map.get("text"), 5000);
            Date date = validator.parseDate(map.get("date"));
            String issuerName = validator.sanitizeString((String) map.get("issuerName"), 100);

            if (text != null && date != null && issuerName != null) {
                String sourceId = validator.sanitizeString((String) map.get("id"), 100);
                String noteId = (sourceId != null && !sourceId.isBlank())
                    ? sourceId
                    : UUID.nameUUIDFromBytes(
                        (text + "|" + date.getTime() + "|" + issuerName).getBytes(StandardCharsets.UTF_8))
                        .toString();
                result.add(new NoteEntry(
                    noteId,
                    text,
                    date,
                    issuerName,
                    null
                ));
            }
        }

        return result;
    }

    private List<Punishment> parsePunishments(Object data) {
        List<Punishment> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;

            Object idObj = map.get("id") != null ? map.get("id") : map.get("_id");
            String id = idObj instanceof String s ? s : null;

            Date issued = validator.parseDate(map.get("issued"));
            if (issued == null) {
                continue;
            }

            String issuerName = validator.sanitizeString((String) map.get("issuerName"), 100);
            if (issuerName == null) {
                issuerName = "Unknown";
            }

            Object typeOrdinalObj = map.get("typeOrdinal");
            int typeOrdinal = 0;
            if (typeOrdinalObj instanceof Number) {
                typeOrdinal = ((Number) typeOrdinalObj).intValue();
            } else if (typeOrdinalObj instanceof String typeOrdinalString) {
                try {
                    typeOrdinal = Integer.parseInt(typeOrdinalString);
                } catch (NumberFormatException ignored) {
                    typeOrdinal = 0;
                }
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
                            notes.add(new PunishmentNote(IdGenerator.generateShortId(), text, date, noteIssuer != null ? noteIssuer : "Unknown", null));
                        }
                    }
                }
            }

            List<PunishmentEvidence> evidence = parseEvidence(map.get("evidence"));

            List<PunishmentModification> modifications = parseModifications(map.get("modifications"));

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

            if (id == null || id.isBlank()) {
                id = "import-" + UUID.nameUUIDFromBytes(
                    (typeOrdinal + "|" + issued.getTime() + "|" + issuerName + "|" + (reason != null ? reason : ""))
                        .getBytes(StandardCharsets.UTF_8)).toString();
            }

            Object activeObj = punishmentData.get("active");
            Object pardonedBy = punishmentData.get("pardonedBy");
            boolean sourceInactive = Boolean.FALSE.equals(activeObj)
                || (activeObj instanceof String activeStr && "false".equalsIgnoreCase(activeStr))
                || pardonedBy != null;
            boolean alreadyPardoned = modifications.stream()
                .anyMatch(m -> PunishmentModificationType.isPardon(m.type()));
            if (sourceInactive && !alreadyPardoned) {
                Date pardonDate = validator.parseDate(punishmentData.get("pardonedDate"));
                if (pardonDate == null) {
                    pardonDate = validator.parseDate(punishmentData.get("removedAt"));
                }
                if (pardonDate == null) {
                    pardonDate = issued;
                }
                String pardonIssuer = pardonedBy instanceof String pardonStr ? pardonStr : "System";
                modifications.add(new PunishmentModification(
                    IdGenerator.generateShortId(),
                    PunishmentModificationType.SYSTEM_PARDON.name(),
                    pardonDate,
                    pardonIssuer,
                    null,
                    "Imported as already removed/inactive",
                    null,
                    null,
                    null
                ));
                punishmentData.put("status", PunishmentStatus.PARDONED);
            }

            Punishment punishment = new Punishment(
                id,
                typeOrdinal,
                issuerName,
                null,
                issued,
                started,
                modifications,
                notes,
                evidence,
                attachedTicketIds,
                punishmentData.isEmpty() ? null : punishmentData
            );

            result.add(punishment);
        }

        return result;
    }

    private List<PunishmentModification> parseModifications(Object data) {
        List<PunishmentModification> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) item;

            String type = validator.sanitizeString((String) m.get("type"), 100);
            if (type == null) {
                continue;
            }
            try {
                PunishmentModificationType.valueOf(type);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Date date = validator.parseDate(m.get("date"));
            if (date == null) {
                continue;
            }

            String sourceId = validator.sanitizeString((String) m.get("id"), 100);
            String id = (sourceId != null && !sourceId.isBlank()) ? sourceId : IdGenerator.generateShortId();

            String issuerName = validator.sanitizeString((String) m.get("issuerName"), 100);
            String issuerId = validator.sanitizeString((String) m.get("issuerId"), 100);

            String reason = validator.sanitizeString((String) m.get("reason"), 1000);
            if (reason == null) {
                reason = "";
            }

            Long effectiveDuration = (m.get("effectiveDuration") instanceof Number n) ? n.longValue() : null;
            String appealTicketId = validator.sanitizeString((String) m.get("appealTicketId"), 100);

            Map<String, Object> modData = null;
            if (m.get("data") instanceof Map<?, ?>) {
                modData = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) m.get("data")).entrySet()) {
                    if (entry.getKey() instanceof String) {
                        modData.put((String) entry.getKey(), entry.getValue());
                    }
                }
            }

            result.add(new PunishmentModification(
                id, type, date, issuerName, issuerId, reason, effectiveDuration, appealTicketId, modData));
        }

        return result;
    }

    private List<PunishmentEvidence> parseEvidence(Object data) {
        List<PunishmentEvidence> result = new ArrayList<>();
        if (!(data instanceof List<?>)) {
            return result;
        }

        for (Object item : (List<?>) data) {
            if (item instanceof String) {
                String text = validator.sanitizeString((String) item, 5000);
                if (text == null || text.isBlank()) {
                    continue;
                }
                result.add(new PunishmentEvidence(text, null, "text", null, null, new Date(), null, null, null));
            } else if (item instanceof Map<?, ?>) {
                Map<?, ?> m = (Map<?, ?>) item;
                String text = validator.sanitizeString((String) m.get("text"), 5000);
                String sanitizedUrl = validator.sanitizeString((String) m.get("url"), 2000);
                String url = SafeUrls.isSafe(sanitizedUrl) ? sanitizedUrl : null;
                String type = validator.sanitizeString((String) m.get("type"), 100);
                if (type == null || type.isBlank()) {
                    type = "link";
                }
                String uploadedBy = validator.sanitizeString((String) m.get("uploadedBy"), 100);
                String uploadedById = validator.sanitizeString((String) m.get("uploadedById"), 100);
                Date uploadedAt = validator.parseDate(m.get("uploadedAt"));
                if (uploadedAt == null) {
                    uploadedAt = new Date();
                }
                String fileName = validator.sanitizeString((String) m.get("fileName"), 500);
                String fileType = validator.sanitizeString((String) m.get("fileType"), 100);
                Long fileSize = (m.get("fileSize") instanceof Number n) ? n.longValue() : null;

                result.add(new PunishmentEvidence(
                    text, url, type, uploadedBy, uploadedById, uploadedAt, fileName, fileType, fileSize));
            }
        }

        return result;
    }

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
