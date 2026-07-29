package gg.modl.backend.audit.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.database.mongo.repository.AuditLogRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.player.data.punishment.Punishment;
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
import gg.modl.backend.infrastructure.util.IdGenerator;
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

    private final AuditLogRepository auditLogRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final StaffMongoRepository staffMongoRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffService staffService;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final PunishmentMutationService punishmentMutationService;

    private final Cache<String, List<ActivePunishmentResponse>> activePunishmentsCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(500)
        .build();

    private static final long PERMANENT_PUNISHMENT_DURATION = -1L;

    public List<PunishmentAuditResponse> getPunishments(
        Server server, int limit, boolean canRollbackOnly) {
        Date thirtyDaysAgo = DateRangeUtil.getStartDate("30d");
        List<AuditLog> logs =
            auditLogRepository.findPunishmentLogs(server, thirtyDaysAgo, limit, canRollbackOnly);

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
        List<Document> rows = punishmentRepository.aggregatePunishmentRows(server);
        Map<String, String> resolvedIssuers = resolveIssuerNames(server, rows);

        List<ActivePunishmentResponse> results = new ArrayList<>();
        for (Document row : rows) {
            Punishment punishment = PunishmentDocumentMapper.reconstructPunishment(row);
            boolean active = statusCalculator.isPunishmentActive(punishment);
            results.add(mapToActivePunishmentResponse(
                server, row, punishment, active, typesByOrdinal, resolvedIssuers));
        }
        return results;
    }

    private ActivePunishmentResponse mapToActivePunishmentResponse(
        Server server, Document row, Punishment punishment, boolean active,
        Map<Integer, PunishmentType> typesByOrdinal, Map<String, String> resolvedIssuers) {
        int typeOrdinal = row.getInteger(AuditProjectionKeys.TYPE_ORDINAL, 0);
        String typeName = punishmentTypeService.getPunishmentTypeName(server, typeOrdinal);
        PunishmentType matchedType = typesByOrdinal.get(typeOrdinal);
        String category = matchedType != null
            ? (matchedType.getCategory() != null ? matchedType.getCategory() : "Administrative")
            : "Administrative";

        Document data = row.get(AuditProjectionKeys.DATA, Document.class);
        String reason = data != null ? data.getString(AuditProjectionKeys.REASON) : null;
        Long duration = extractDuration(data);
        List<ActivePunishmentResponse.EvidenceItem> evidenceItems = PunishmentDocumentMapper.extractEvidenceItems(row);

        List<String> ticketIds = row.getList("attachedTicketIds", String.class);
        if (ticketIds == null) {
            ticketIds = Collections.emptyList();
        }

        return new ActivePunishmentResponse(
            row.getString(AuditProjectionKeys.PUNISHMENT_ID),
            row.getString(AuditProjectionKeys.PLAYER_ID),
            AuditDocumentUtil.extractPlayerNameFromDoc(row),
            typeName,
            typeOrdinal,
            category,
            resolveIssuerFromDoc(
                row.getString(AuditProjectionKeys.ISSUER_ID),
                row.getString(AuditProjectionKeys.ISSUER_NAME),
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
        Object durationObj = data.get(AuditProjectionKeys.DURATION);
        if (durationObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Map<String, String> resolveIssuerNames(Server server, List<Document> rows) {
        Set<String> issuerIds = new HashSet<>();
        for (Document doc : rows) {
            String issuerId = doc.getString(AuditProjectionKeys.ISSUER_ID);
            if (issuerId != null) {
                issuerIds.add(issuerId);
            }
        }
        return staffMongoRepository.findUsernamesByIds(server, issuerIds);
    }

    public boolean rollbackPunishment(
        Server server, String punishmentId, String reason, String performerUsername) {
        Document player = punishmentRepository.findPlayerByPunishmentId(server, punishmentId);
        if (player == null) {
            return false;
        }
        Document punishment = findPunishmentSubdocument(player, punishmentId);
        if (punishment == null) {
            return false;
        }
        if (AuditDocumentUtil.hasModificationType(punishment, PunishmentModificationType.ROLLBACK.name())) {
            throw new ValidationException("This punishment cannot be rolled back");
        }

        Date now = new Date();
        String playerId = player.getString("_id");
        punishmentRepository.appendPunishmentModification(
            server, playerId, punishmentId, buildRollbackModification(performerUsername, reason, now));
        saveRollbackAuditLog(
            server, playerId,
            AuditDocumentUtil.extractPlayerNameFromDoc(player), punishment,
            reason, performerUsername, now,
            false, Objects.toString(punishment.getString("issuerName"), ""));
        return true;
    }

    private Map<String, Object> buildRollbackModification(String performerUsername, String reason, Date now) {
        Map<String, Object> modification = new HashMap<>();
        modification.put("id", IdGenerator.generateShortId());
        modification.put("type", PunishmentModificationType.ROLLBACK.name());
        modification.put("date", now);
        modification.put("issuerName", performerUsername);
        modification.put("reason", reason != null ? reason : "Rollback");
        return modification;
    }

    private Document findPunishmentSubdocument(Document player, String punishmentId) {
        List<Document> punishments = player.getList("punishments", Document.class);
        if (punishments == null) {
            return null;
        }
        for (Document punishment : punishments) {
            if (punishmentId.equals(punishment.getString("id"))) {
                return punishment;
            }
        }
        return null;
    }

    private void saveRollbackAuditLog(
        Server server, String playerId, String playerName, Document punishment,
        String reason, String performerUsername, Date now, boolean bulk, String issuerUsername) {
        int typeOrdinal = punishment.getInteger("typeOrdinal", 0);
        String typeName = punishmentTypeService.getPunishmentTypeName(server, typeOrdinal);
        String punishmentId = punishment.getString("id");

        String description = bulk
            ? "Bulk rollback: " + typeName + " for " + playerName + " (issued by " + issuerUsername + ")"
            : "Rolled back " + typeName + " for " + (playerName.isEmpty() ? "unknown player" : playerName);

        AuditLog rollbackLog = AuditLog.builder()
            .created(now)
            .level("moderation")
            .source(performerUsername)
            .description(description)
            .metadata(Map.of(
                "punishmentId", punishmentId != null ? punishmentId : "",
                "playerId", playerId != null ? playerId : "",
                "playerName", playerName,
                "staffUsername", issuerUsername,
                "rollbackReason", reason != null ? reason : (bulk ? "Bulk rollback" : "Admin rollback"),
                "punishmentType", typeName,
                "bulkRollback", bulk
            ))
            .build();

        auditLogRepository.saveAuditLog(server, rollbackLog);
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
                punishmentRepository.findPlayersForRollback(server, staffUsername, staffId);
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

        for (Document punishment : punishments) {
            if (!matchesIssuer(punishment, staffUsername, staffId)) {
                continue;
            }
            if (!isWithinDateRange(punishment.getDate("issued"), startDate, endDate)) {
                continue;
            }
            if (AuditDocumentUtil.hasModificationType(punishment, PunishmentModificationType.ROLLBACK.name())) {
                continue;
            }

            punishmentRepository.appendPunishmentModification(
                server, playerId, punishment.getString("id"),
                buildRollbackModification(performerUsername, reason, now));
            saveRollbackAuditLog(
                server, playerId, playerName, punishment,
                reason, performerUsername, now, true, staffUsername);
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

                PunishmentOperationResult result = punishmentLifecycleService.pardonPunishment(
                    server, ctx.punishmentId, performerUsername, null, reason);
                if (!result.success()) {
                    return false;
                }

                AuditLog pardonLog = buildBulkAuditLog(ctx, performerUsername,
                    "Bulk pardon: " + ctx.typeName + " for " + ctx.playerName,
                    Map.of("pardonReason", reason != null ? reason : "", "bulkPardon", true));
                auditLogRepository.saveAuditLog(server, pardonLog);
                return true;
            });
    }

    public int bulkSetExpirationByType(
        Server server, List<Integer> typeOrdinals, long newDurationMs,
        String reason, String performerUsername) {
        long effectiveDuration = newDurationMs <= 0 ? PERMANENT_PUNISHMENT_DURATION : newDurationMs;

        return processBulkPunishmentAction(server, typeOrdinals, reason, performerUsername,
            "bulk set expiration", (ctx) -> {
                PunishmentOperationResult result = punishmentMutationService.changeDuration(
                    server, ctx.punishmentId, effectiveDuration, performerUsername, null);
                if (!result.success()) {
                    return false;
                }

                AuditLog durationLog = buildBulkAuditLog(ctx, performerUsername,
                    "Bulk duration change: " + ctx.typeName + " for " + ctx.playerName,
                    Map.of("reason", reason != null ? reason : "", "newDurationMs", newDurationMs,
                        "bulkDurationChange", true));
                auditLogRepository.saveAuditLog(server, durationLog);
                return true;
            });
    }

    private int processBulkPunishmentAction(
        Server server, List<Integer> typeOrdinals, String reason,
        String performerUsername, String operationName, BulkPunishmentAction action) {
        try {
            List<Document> players = punishmentRepository.findPlayersForBulkAction(server, typeOrdinals);
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
                    int typeOrdinal = punishmentDoc.getInteger(AuditProjectionKeys.TYPE_ORDINAL, 0);
                    if (!typeOrdinals.contains(typeOrdinal)) {
                        continue;
                    }

                    Punishment punishment = PunishmentDocumentMapper.reconstructPunishment(punishmentDoc);
                    if (!statusCalculator.isPunishmentActive(punishment)) {
                        continue;
                    }

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
