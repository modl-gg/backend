package gg.modl.backend.audit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.player.service.PunishmentMutationService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditMongoRepository auditRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffService staffService;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final PunishmentMutationService punishmentMutationService;

    private final Cache<String, List<ActivePunishmentResponse>> activePunishmentsCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(500)
        .build();

    // Real collection names; the single canonical allowlist for the raw DB viewer (shared with AuditController).
    public static final Set<String> ALLOWED_TABLES = Set.of(
        CollectionName.PLAYERS,
        CollectionName.SETTINGS,
        CollectionName.STAFF,
        CollectionName.STAFF_ROLES,
        CollectionName.TICKETS,
        CollectionName.TICKET_VERIFICATIONS,
        CollectionName.LOGS,
        CollectionName.KNOWLEDGEBASE_CATEGORIES,
        CollectionName.KNOWLEDGEBASE_ARTICLES,
        CollectionName.HOMEPAGE_CARDS
    );

    // Settings doc types that store plaintext secrets in their `data` payload.
    private static final Set<String> SECRET_SETTINGS_TYPES =
        Set.of("apiKeys", "webhookSettings", "aiModerationSettings", "domain");
    // Top-level field names whose values are redacted across every dumped collection (case-insensitive contains).
    private static final List<String> SECRET_FIELD_NAMES = List.of(
        "api_key", "ticket_api_key", "minecraft_api_key", "apiKey", "webhookUrl", "token", "secret", "password");
    private static final String REDACTED = "[REDACTED]";

    public List<PunishmentAuditResponse> getPunishments(
        Server server, int limit, boolean canRollbackOnly) {
        Date thirtyDaysAgo = DateRangeUtil.getStartDate("30d");
        List<AuditLog> logs =
            auditRepository.findPunishmentLogs(server, thirtyDaysAgo, limit, canRollbackOnly);

        return logs.stream().map(logEntry -> {
            Map<String, Object> metadata = logEntry.getMetadata() != null
                                           ? logEntry.getMetadata() : Collections.emptyMap();
            return new PunishmentAuditResponse(
                logEntry.getId(),
                extractPunishmentType(logEntry.getDescription()),
                getStringFromMetadata(metadata, "playerId", "unknown"),
                getStringFromMetadata(metadata, "playerName", "Unknown"),
                getStringFromMetadata(metadata, "staffId", logEntry.getSource()),
                logEntry.getSource(),
                getStringFromMetadata(metadata, "reason",
                    logEntry.getDescription()),
                getStringFromMetadata(metadata, "duration", null),
                logEntry.getCreated(),
                !Boolean.FALSE.equals(metadata.get("canRollback"))
            );
        }).toList();
    }

    private String extractPunishmentType(String description) {
        if (description == null) {
            return "Unknown";
        }
        String lower = description.toLowerCase();
        if (lower.contains("ban")) {
            return "Ban";
        }
        if (lower.contains("mute")) {
            return "Mute";
        }
        if (lower.contains("kick")) {
            return "Kick";
        }
        if (lower.contains("warn")) {
            return "Warn";
        }
        return "Unknown";
    }

    private String getStringFromMetadata(
        Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return defaultValue;
    }

    public List<ActivePunishmentResponse> getActivePunishments(Server server) {
        return getPunishmentsList(server, "active");
    }

    public List<ActivePunishmentResponse> getPunishmentsList(Server server, String statusFilter) {
        List<ActivePunishmentResponse> all =
            activePunishmentsCache.get(server.getId(), key -> computeAllPunishments(server));

        boolean filterActive = "active".equalsIgnoreCase(statusFilter);
        boolean filterInactive = "inactive".equalsIgnoreCase(statusFilter);

        List<ActivePunishmentResponse> results = new ArrayList<>();
        for (ActivePunishmentResponse punishment : all) {
            if (filterActive && !punishment.active()) {
                continue;
            }
            if (filterInactive && punishment.active()) {
                continue;
            }
            results.add(punishment);
        }
        return results;
    }

    private List<ActivePunishmentResponse> computeAllPunishments(Server server) {
        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        Map<Integer, PunishmentType> typesByOrdinal = PunishmentTypeIndex.byOrdinal(punishmentTypes);
        List<Document> rows = auditRepository.aggregatePunishmentRows(server);
        Map<String, String> resolvedIssuers = resolveIssuerNames(server, rows);

        List<ActivePunishmentResponse> results = new ArrayList<>();
        for (Document row : rows) {
            Punishment punishment = reconstructPunishment(row);
            boolean active = statusCalculator.isPunishmentActive(punishment);
            results.add(mapToActivePunishmentResponse(
                server, row, punishment, active, typesByOrdinal, resolvedIssuers));
        }
        return results;
    }

    private ActivePunishmentResponse mapToActivePunishmentResponse(
        Server server, Document row, Punishment punishment, boolean active,
        Map<Integer, PunishmentType> typesByOrdinal, Map<String, String> resolvedIssuers) {
        int typeOrdinal = row.getInteger("typeOrdinal", 0);
        String typeName = punishmentTypeService.getPunishmentTypeName(server, typeOrdinal);
        PunishmentType matchedType = typesByOrdinal.get(typeOrdinal);
        String category = matchedType != null
            ? (matchedType.getCategory() != null ? matchedType.getCategory() : "Administrative")
            : "Administrative";

        Document data = row.get("data", Document.class);
        String reason = data != null ? data.getString("reason") : null;
        Long duration = extractDuration(data);
        List<ActivePunishmentResponse.EvidenceItem> evidenceItems = extractEvidenceItems(row);

        List<String> ticketIds = row.getList("attachedTicketIds", String.class);
        if (ticketIds == null) {
            ticketIds = Collections.emptyList();
        }

        return new ActivePunishmentResponse(
            row.getString("punishmentId"),
            row.getString("playerId"),
            AuditDocumentUtil.extractPlayerNameFromDoc(row),
            typeName,
            typeOrdinal,
            category,
            resolveIssuerFromDoc(
                row.getString("issuerId"),
                row.getString("issuerName"),
                resolvedIssuers),
            reason,
            duration,
            row.getDate("issued"),
            row.getDate("started"),
            statusCalculator.getEffectiveExpiry(punishment),
            active,
            !evidenceItems.isEmpty(),
            evidenceItems.size(),
            evidenceItems,
            ticketIds
        );
    }

    private List<ActivePunishmentResponse.EvidenceItem> extractEvidenceItems(Document row) {
        List<Document> evidenceDocs = row.getList("evidence", Document.class);
        if (evidenceDocs == null) {
            return Collections.emptyList();
        }

        List<ActivePunishmentResponse.EvidenceItem> items = new ArrayList<>();
        for (Document evidenceDoc : evidenceDocs) {
            items.add(new ActivePunishmentResponse.EvidenceItem(
                evidenceDoc.getString("text"),
                evidenceDoc.getString("url"),
                evidenceDoc.getString("type"),
                evidenceDoc.getString("fileName")
            ));
        }
        return items;
    }

    private static String resolveIssuerFromDoc(
        String issuerId, String issuerName, Map<String, String> resolvedIssuers) {
        if (issuerId != null && resolvedIssuers.containsKey(issuerId)) {
            return resolvedIssuers.get(issuerId);
        }
        if (issuerName != null) {
            return issuerName;
        }
        return issuerId != null ? "Unknown Staff" : "Console";
    }

    private Long extractDuration(Document data) {
        if (data == null) {
            return null;
        }
        Object durationObj = data.get("duration");
        if (durationObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Map<String, String> resolveIssuerNames(Server server, List<Document> rows) {
        Set<String> issuerIds = new HashSet<>();
        for (Document doc : rows) {
            String issuerId = doc.getString("issuerId");
            if (issuerId != null) {
                issuerIds.add(issuerId);
            }
        }
        return auditRepository.mapStaffUsernamesByIds(server, issuerIds);
    }

    private Punishment reconstructPunishment(Document doc) {
        Punishment punishment = new Punishment();
        // Aggregate rows alias the id as "punishmentId"; raw embedded subdocs carry the native "id" field.
        String reconstructedId = doc.getString("punishmentId");
        if (reconstructedId == null) {
            reconstructedId = doc.getString("id");
        }
        punishment.setId(reconstructedId);
        punishment.setTypeOrdinal(doc.getInteger("typeOrdinal", 0));
        punishment.setIssuerName(
            doc.getString("issuerName") != null ? doc.getString("issuerName") : "Unknown");
        punishment.setIssuerId(doc.getString("issuerId"));
        punishment.setIssued(
            doc.getDate("issued") != null ? doc.getDate("issued") : new Date());
        punishment.setStarted(doc.getDate("started"));

        Document data = doc.get("data", Document.class);
        if (data != null) {
            punishment.setData(new HashMap<>(data));
        }

        punishment.setModifications(extractModifications(doc));
        punishment.setNotes(Collections.emptyList());
        punishment.setEvidence(Collections.emptyList());
        punishment.setAttachedTicketIds(Collections.emptyList());

        return punishment;
    }

    private List<PunishmentModification> extractModifications(Document doc) {
        List<Document> modDocs = doc.getList("modifications", Document.class);
        if (modDocs == null) {
            return new ArrayList<>();
        }

        List<PunishmentModification> mods = new ArrayList<>();
        for (Document modDoc : modDocs) {
            Long effectiveDuration = null;
            Object edObj = modDoc.get("effectiveDuration");
            if (edObj instanceof Number num) {
                effectiveDuration = num.longValue();
            }
            mods.add(new PunishmentModification(
                modDoc.getString("id"),
                modDoc.getString("type"),
                modDoc.getDate("date"),
                modDoc.getString("issuerName"),
                modDoc.getString("issuerId"),
                modDoc.getString("reason"),
                effectiveDuration,
                modDoc.getString("appealTicketId"),
                null
            ));
        }
        return mods;
    }

    public boolean rollbackPunishment(
        Server server, String punishmentId, String reason, String performerUsername) {
        AuditLog punishment = auditRepository.findAuditLogById(server, punishmentId);
        if (punishment == null) {
            return false;
        }

        Map<String, Object> metadata = punishment.getMetadata();
        if (metadata != null && Boolean.FALSE.equals(metadata.get("canRollback"))) {
            throw new ValidationException("This punishment cannot be rolled back");
        }

        // Map.getOrDefault returns a PRESENT-but-null value as-is, and Map.of forbids nulls -> NPE/500.
        // Coalesce explicitly so present-null playerName/reason/source render as "" instead of throwing.
        String playerName = metadata != null ? Objects.toString(metadata.get("playerName"), "") : "";
        String originalReason = metadata != null ? Objects.toString(metadata.get("reason"), "") : "";

        AuditLog rollbackLog = AuditLog.builder()
            .created(new Date())
            .level("moderation")
            .source(performerUsername)
            .description("Rolled back " + extractPunishmentType(punishment.getDescription())
                         + " for "
                         + (playerName.isEmpty() ? "unknown player" : playerName))
            .metadata(Map.of(
                "originalPunishmentId", punishmentId,
                "rollbackReason", reason != null ? reason : "Admin rollback",
                "originalPunishment", Map.of(
                    "type", extractPunishmentType(punishment.getDescription()),
                    "player", playerName,
                    "staff", Objects.toString(punishment.getSource(), ""),
                    "originalReason", originalReason
                )
            ))
            .build();

        auditRepository.saveAuditLog(server, rollbackLog);
        auditRepository.markAuditLogRolledBack(
            server, punishmentId, performerUsername, new Date());
        return true;
    }

    public Map<String, Object> getDatabaseTable(
        Server server, String table, int limit, int skip) {
        // Single canonical allowlist (real collection names). The controller pre-checks the same set;
        // re-assert here so `table` is always a real collection name before it reaches the repository.
        if (!ALLOWED_TABLES.contains(table)) {
            throw new ValidationException("Invalid table name");
        }

        List<Document> documents = auditRepository.readTable(server, table, limit, skip);
        long total = auditRepository.countCollection(server, table);

        return Map.of(
            "data", redactDocuments(table, documents),
            "total", total,
            "limit", limit,
            "skip", skip
        );
    }

    // Defense in depth: never let the generic DB viewer leak plaintext secrets, even to a super-admin.
    // Returns COPIES so the managed Mongo Document instances are never mutated on the read path.
    private List<Document> redactDocuments(String table, List<Document> docs) {
        if (docs == null) {
            return Collections.emptyList();
        }
        List<Document> redacted = new ArrayList<>(docs.size());
        for (Document orig : docs) {
            Document copy = new Document(orig);
            if (CollectionName.SETTINGS.equals(table) && SECRET_SETTINGS_TYPES.contains(copy.getString("type"))) {
                copy.put("data", REDACTED);
            }
            for (String key : new ArrayList<>(copy.keySet())) {
                String lowerKey = key.toLowerCase();
                if (SECRET_FIELD_NAMES.stream().anyMatch(secret -> lowerKey.contains(secret.toLowerCase()))) {
                    copy.put(key, REDACTED);
                }
            }
            redacted.add(copy);
        }
        return redacted;
    }

    public int rollbackAllPunishmentsByStaff(
        Server server, String staffUsername, String reason, String performerUsername) {
        String staffId = staffService.getStaffByUsername(server, staffUsername)
            .map(StaffResponse::id)
            .orElse(null);
        return rollbackPunishmentsInternal(
            server, staffUsername, staffId, null, null, reason, performerUsername);
    }

    private int rollbackPunishmentsInternal(
        Server server, String staffUsername, String staffId,
        Date startDate, Date endDate, String reason, String performerUsername) {
        try {
            List<Document> players =
                auditRepository.findPlayersForRollback(server, staffUsername, staffId);
            Date now = new Date();

            int rollbackCount = 0;
            for (Document player : players) {
                rollbackCount += applyRollbackToPlayer(
                    server, player, staffUsername, staffId,
                    startDate, endDate, reason, performerUsername, now);
            }
            return rollbackCount;
        } catch (Exception e) {
            log.error("Error during bulk rollback for staff {}", staffUsername, e);
            throw new ExternalServiceException("Failed to rollback punishments", e);
        }
    }

    private int applyRollbackToPlayer(
        Server server, Document player, String staffUsername, String staffId,
        Date startDate, Date endDate, String reason, String performerUsername, Date now) {
        String playerId = player.getString("_id");
        List<Document> punishments = player.getList("punishments", Document.class);
        if (punishments == null) {
            return 0;
        }

        String playerName = AuditDocumentUtil.extractPlayerNameFromDoc(player);
        int count = 0;

        // Bulk rollback is audit-only (ROLLBACK is not a pardon and never mutated the punishment),
        // matching single rollbackPunishment. It is intentionally repeatable and writes per-punishment
        // AuditLogs only; the misleading malformed ROLLBACK modification write has been removed.
        for (Document punishment : punishments) {
            if (!matchesIssuer(punishment, staffUsername, staffId)) {
                continue;
            }
            if (!isWithinDateRange(punishment.getDate("issued"), startDate, endDate)) {
                continue;
            }

            // Embedded punishment subdocs key their short id under the native "id" field, not "_id".
            String punishmentId = punishment.getString("id");

            int typeOrdinal = punishment.getInteger("typeOrdinal", 0);
            String typeName =
                punishmentTypeService.getPunishmentTypeName(server, typeOrdinal);

            AuditLog rollbackLog = AuditLog.builder()
                .created(now)
                .level("moderation")
                .source(performerUsername)
                .description("Bulk rollback: " + typeName + " for " + playerName
                             + " (issued by " + staffUsername + ")")
                .metadata(Map.of(
                    "punishmentId", punishmentId != null ? punishmentId : "",
                    "playerId", playerId != null ? playerId : "",
                    "playerName", playerName,
                    "staffUsername", staffUsername,
                    "rollbackReason", reason != null ? reason : "Bulk rollback",
                    "punishmentType", typeName,
                    "bulkRollback", true
                ))
                .build();

            auditRepository.saveAuditLog(server, rollbackLog);
            count++;
        }

        return count;
    }

    private boolean matchesIssuer(
        Document punishment, String staffUsername, String staffId) {
        String issuerName = punishment.getString("issuerName");
        String issuerId = punishment.getString("issuerId");
        return (issuerName != null && issuerName.equalsIgnoreCase(staffUsername))
               || (staffId != null && staffId.equals(issuerId));
    }

    private boolean isWithinDateRange(Date issued, Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return true;
        }
        return issued != null && !issued.before(startDate) && !issued.after(endDate);
    }

    public int rollbackPunishmentsByDateRange(
        Server server, String staffUsername, Date startDate, Date endDate,
        String reason, String performerUsername) {
        String staffId = staffService.getStaffByUsername(server, staffUsername)
            .map(StaffResponse::id)
            .orElse(null);
        return rollbackPunishmentsInternal(
            server, staffUsername, staffId, startDate, endDate, reason, performerUsername);
    }

    public int bulkPardonByType(
        Server server, List<Integer> typeOrdinals, String reason, String performerUsername) {
        return processBulkPunishmentAction(server, typeOrdinals, reason, performerUsername,
            "bulk pardon", (ctx) -> {
                if (AuditDocumentUtil.hasModificationType(ctx.punishmentDoc,
                        PunishmentModificationType.MANUAL_PARDON.name(),
                        PunishmentModificationType.APPEAL_ACCEPT.name(),
                        PunishmentModificationType.SYSTEM_PARDON.name())) {
                    return false;
                }

                // Route through the canonical pardon entry point so the bulk path matches single-pardon:
                // MANUAL_PARDON modification + pardoned/reason notes + data.status=Pardoned + realtime
                // push (plugin un-enforces in-game, panel invalidates) + alt-blocking linked-ban cascade.
                PunishmentOperationResult result = punishmentLifecycleService.pardonPunishment(
                    server, ctx.punishmentId, performerUsername, null, reason);
                if (!result.success()) {
                    return false;
                }

                AuditLog pardonLog = buildBulkAuditLog(ctx, performerUsername,
                    "Bulk pardon: " + ctx.typeName + " for " + ctx.playerName,
                    Map.of("pardonReason", reason != null ? reason : "", "bulkPardon", true));
                auditRepository.saveAuditLog(server, pardonLog);
                return true;
            });
    }

    public int bulkSetExpirationByType(
        Server server, List<Integer> typeOrdinals, long newDurationMs,
        String reason, String performerUsername) {
        // -1L is the explicit permanent sentinel (getEffectiveExpiry treats duration <= 0 as permanent);
        // a null/omitted duration would leave the original finite expiry in place (silent no-op).
        long effectiveDuration = newDurationMs <= 0 ? -1L : newDurationMs;

        return processBulkPunishmentAction(server, typeOrdinals, reason, performerUsername,
            "bulk set expiration", (ctx) -> {
                // Route through the canonical duration-change entry point so the bulk path matches the
                // single change: MANUAL_DURATION_CHANGE + data.duration write + realtime push (plugin
                // recomputes expiry / releases shortened bans) + alt-blocking linked-ban cascade.
                PunishmentOperationResult result = punishmentMutationService.changeDuration(
                    server, ctx.punishmentId, effectiveDuration, performerUsername, null);
                if (!result.success()) {
                    return false;
                }

                AuditLog durationLog = buildBulkAuditLog(ctx, performerUsername,
                    "Bulk duration change: " + ctx.typeName + " for " + ctx.playerName,
                    Map.of("reason", reason != null ? reason : "", "newDurationMs", newDurationMs,
                        "bulkDurationChange", true));
                auditRepository.saveAuditLog(server, durationLog);
                return true;
            });
    }

    private int processBulkPunishmentAction(
        Server server, List<Integer> typeOrdinals, String reason,
        String performerUsername, String operationName, BulkPunishmentAction action) {
        try {
            List<Document> players = auditRepository.findPlayersForBulkAction(server, typeOrdinals);
            Date now = new Date();
            int count = 0;

            Map<Integer, String> typeNameCache = new HashMap<>();
            for (int ordinal : typeOrdinals) {
                typeNameCache.put(ordinal, punishmentTypeService.getPunishmentTypeName(server, ordinal));
            }

            for (Document player : players) {
                String playerId = player.getString("_id");
                List<Document> punishments = player.getList("punishments", Document.class);
                if (punishments == null) {
                    continue;
                }

                String playerName = AuditDocumentUtil.extractPlayerNameFromDoc(player);

                for (Document punishmentDoc : punishments) {
                    int typeOrdinal = punishmentDoc.getInteger("typeOrdinal", 0);
                    if (!typeOrdinals.contains(typeOrdinal)) {
                        continue;
                    }

                    Punishment punishment = reconstructPunishment(punishmentDoc);
                    if (!statusCalculator.isPunishmentActive(punishment)) {
                        continue;
                    }

                    // Embedded punishment subdocs key their short id under the native "id" field, not "_id".
                    String punishmentId = punishmentDoc.getString("id");
                    String typeName = typeNameCache.getOrDefault(typeOrdinal, "Unknown");

                    BulkActionContext ctx = new BulkActionContext(
                        playerId, playerName, punishmentId, punishmentDoc, typeName, typeOrdinal, now);
                    if (action.apply(ctx)) {
                        count++;
                    }
                }
            }
            activePunishmentsCache.invalidate(server.getId());
            return count;
        } catch (Exception e) {
            log.error("Error during {}", operationName, e);
            throw new ExternalServiceException("Failed to " + operationName, e);
        }
    }

    private AuditLog buildBulkAuditLog(
        BulkActionContext ctx, String performerUsername,
        String description, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("punishmentId", ctx.punishmentId != null ? ctx.punishmentId : "");
        metadata.put("playerId", ctx.playerId != null ? ctx.playerId : "");
        metadata.put("playerName", ctx.playerName);
        metadata.put("punishmentType", ctx.typeName);
        metadata.putAll(extraMetadata);

        return AuditLog.builder()
            .created(ctx.now)
            .level("moderation")
            .source(performerUsername)
            .description(description)
            .metadata(metadata)
            .build();
    }

    private record BulkActionContext(
        String playerId, String playerName, String punishmentId,
        Document punishmentDoc, String typeName, int typeOrdinal, Date now) {}

    @FunctionalInterface
    private interface BulkPunishmentAction {
        boolean apply(BulkActionContext ctx);
    }
}
