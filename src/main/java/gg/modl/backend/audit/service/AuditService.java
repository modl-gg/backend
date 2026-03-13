package gg.modl.backend.audit.service;

import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.audit.dto.response.ActivePunishmentResponse;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.IdCountResult;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.OrdinalCountResult;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.StaffActivityResult;
import gg.modl.backend.exception.ValidationException;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.StaffService;
import gg.modl.backend.util.DateRangeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditMongoRepository auditRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffService staffService;
    private final PlayerStatusCalculator statusCalculator;

    public List<StaffPerformanceResponse> getStaffPerformance(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        List<Staff> allStaff = auditRepository.findAllStaff(server);
        Map<String, StaffActivityResult> activityByUsername = indexStaffActivity(
            auditRepository.aggregateLogActivityBySource(server, startDate));
        Map<String, Integer> ticketResponsesByStaff = indexIdCounts(
            auditRepository.aggregateTicketResponseCounts(server, startDate));
        Map<String, Integer> punishmentsByStaff = countPunishmentsByStaff(server, startDate);

        List<StaffPerformanceResponse> performanceList = new ArrayList<>();
        for (Staff staff : allStaff) {
            String username = staff.getUsername();
            if (username == null) {
                continue;
            }

            String lowerUsername = username.toLowerCase();
            StaffActivityResult activity = activityByUsername.get(lowerUsername);
            int totalActions = activity != null ? activity.totalActions() : 0;
            int ticketActions = ticketResponsesByStaff.getOrDefault(lowerUsername, 0);

            int moderationActions = punishmentsByStaff.getOrDefault(lowerUsername, 0);
            String minecraftUsername = staff.getAssignedMinecraftUsername();
            if (minecraftUsername != null && !minecraftUsername.isEmpty()
                && !minecraftUsername.equalsIgnoreCase(username)) {
                moderationActions +=
                    punishmentsByStaff.getOrDefault(minecraftUsername.toLowerCase(), 0);
            }
            if (staff.getId() != null) {
                moderationActions +=
                    punishmentsByStaff.getOrDefault(staff.getId().toLowerCase(), 0);
            }

            Date lastActive = activity != null
                              ? activity.lastActive() : staff.getUpdatedAt();
            if (ticketActions > 0 || moderationActions > 0) {
                totalActions = Math.max(totalActions, ticketActions + moderationActions);
            }

            performanceList.add(new StaffPerformanceResponse(
                staff.getId(),
                username,
                staff.getRole() != null ? staff.getRole() : "User",
                totalActions,
                ticketActions,
                moderationActions,
                60,
                lastActive != null ? lastActive : new Date()
            ));
        }

        performanceList.sort(
            (left, right) -> Integer.compare(right.totalActions(), left.totalActions()));
        return performanceList;
    }

    private Map<String, StaffActivityResult> indexStaffActivity(List<StaffActivityResult> results) {
        Map<String, StaffActivityResult> map = new HashMap<>();
        for (StaffActivityResult result : results) {
            if (result.id() != null) {
                map.put(result.id().toLowerCase(), result);
            }
        }
        return map;
    }

    private Map<String, Integer> indexIdCounts(List<IdCountResult> results) {
        Map<String, Integer> map = new HashMap<>();
        for (IdCountResult result : results) {
            if (result.id() != null) {
                map.put(result.id().toLowerCase(), result.count());
            }
        }
        return map;
    }

    private Map<String, Integer> countPunishmentsByStaff(Server server, Date startDate) {
        Map<String, Integer> counts = new HashMap<>();
        List<IdCountResult> results =
            auditRepository.aggregatePunishmentCountsByIssuer(server, startDate);

        Set<String> issuerIdsToResolve = new HashSet<>();
        for (IdCountResult result : results) {
            if (result.id() != null && ObjectId.isValid(result.id())) {
                issuerIdsToResolve.add(result.id());
            }
        }
        Map<String, String> resolvedIds =
            auditRepository.mapStaffUsernamesByIds(server, issuerIdsToResolve);

        for (IdCountResult result : results) {
            if (result.id() == null) {
                continue;
            }
            String displayName = resolvedIds.getOrDefault(result.id(), result.id());
            counts.merge(displayName.toLowerCase(), result.count(), Integer::sum);
        }
        return counts;
    }

    public StaffDetailsResponse getStaffDetails(Server server, String username, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        List<String> usernamesToSearch = new ArrayList<>();
        usernamesToSearch.add(username);
        String staffId = null;

        Optional<StaffResponse> staffOpt = staffService.getStaffByUsername(server, username);
        if (staffOpt.isPresent()) {
            StaffResponse staff = staffOpt.get();
            staffId = staff.id();
            if (staff.assignedMinecraftUsername() != null
                && !staff.assignedMinecraftUsername().isEmpty()
                && !staff.assignedMinecraftUsername().equalsIgnoreCase(username)) {
                usernamesToSearch.add(staff.assignedMinecraftUsername());
            }
        }

        List<StaffDetailsResponse.PunishmentDetail> punishments =
            getPunishmentDetails(server, usernamesToSearch, staffId, startDate);
        List<StaffDetailsResponse.TicketDetail> tickets =
            getTicketDetails(server, username, startDate);
        List<StaffDetailsResponse.DailyActivity> dailyActivity =
            getDailyActivity(server, usernamesToSearch, staffId, startDate);
        List<StaffDetailsResponse.PunishmentTypeBreakdown> typeBreakdown =
            getPunishmentTypeBreakdown(server, usernamesToSearch, staffId, startDate);

        long evidenceUploads = auditRepository.countEvidenceUploads(server, username, startDate);
        int avgResponseTime = tickets.isEmpty()
                              ? 0
                              : (int) tickets.stream()
                                  .mapToInt(StaffDetailsResponse.TicketDetail::responseTime)
                                  .average()
                                  .orElse(0);

        StaffDetailsResponse.Summary summary = new StaffDetailsResponse.Summary(
            punishments.size(),
            tickets.size(),
            avgResponseTime,
            (int) evidenceUploads
        );

        return new StaffDetailsResponse(
            username,
            period,
            punishments,
            tickets,
            dailyActivity,
            typeBreakdown,
            (int) evidenceUploads,
            summary
        );
    }

    private List<StaffDetailsResponse.PunishmentDetail> getPunishmentDetails(
        Server server, List<String> usernames, String staffId, Date startDate) {
        List<StaffDetailsResponse.PunishmentDetail> details = new ArrayList<>();
        List<Document> results =
            auditRepository.aggregatePunishmentDetails(server, usernames, staffId, startDate);

        for (Document doc : results) {
            int typeOrdinal = doc.getInteger("typeOrdinal", 0);
            String reason = doc.getString("reason");
            Object durationObj = doc.get("duration");

            details.add(new StaffDetailsResponse.PunishmentDetail(
                doc.getString("punishmentId"),
                doc.getString("playerId"),
                extractPlayerNameFromDoc(doc),
                punishmentTypeService.getPunishmentTypeName(server, typeOrdinal),
                reason != null ? reason : "No reason provided",
                durationObj != null ? durationObj.toString() : null,
                doc.getDate("issued"),
                !hasModificationType(doc, "REMOVE", "REVOKE"),
                hasModificationType(doc, "ROLLBACK")
            ));
        }
        return details;
    }

    private String extractPlayerNameFromDoc(Document doc) {
        List<Document> usernames = doc.getList("usernames", Document.class);
        if (usernames != null && !usernames.isEmpty()) {
            String name = usernames.get(0).getString("username");
            if (name != null) {
                return name;
            }
        }
        return "Unknown";
    }

    private boolean hasModificationType(Document doc, String... types) {
        List<?> modifications = doc.getList("modifications", Document.class);
        if (modifications == null) {
            return false;
        }
        for (Object mod : modifications) {
            if (mod instanceof Document modDoc) {
                String modType = modDoc.getString("type");
                for (String type : types) {
                    if (type.equalsIgnoreCase(modType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<StaffDetailsResponse.TicketDetail> getTicketDetails(
        Server server, String username, Date startDate) {
        List<StaffDetailsResponse.TicketDetail> details = new ArrayList<>();
        List<Document> results =
            auditRepository.aggregateTicketDetails(server, username, startDate);

        for (Document doc : results) {
            int responseTime = calculateResponseTimeMinutes(
                doc.getDate("ticketCreated"), doc.getDate("firstReply"));

            String subject = doc.getString("subject");
            String category = doc.getString("category");
            String status = doc.getString("status");

            details.add(new StaffDetailsResponse.TicketDetail(
                doc.getString("_id"),
                subject != null ? subject : "No Subject",
                category != null ? category : "General",
                status != null ? status : "Unknown",
                doc.getDate("lastActivity"),
                responseTime
            ));
        }
        return details;
    }

    private int calculateResponseTimeMinutes(Date ticketCreated, Date firstReply) {
        if (ticketCreated == null || firstReply == null) {
            return 0;
        }
        long diffMs = firstReply.getTime() - ticketCreated.getTime();
        return (int) (diffMs / (1000 * 60));
    }

    private List<StaffDetailsResponse.DailyActivity> getDailyActivity(
        Server server, List<String> usernames, String staffId, Date startDate) {
        Map<String, StaffDetailsResponse.DailyActivity> activityByDate = new HashMap<>();

        List<IdCountResult> punishmentResults =
            auditRepository.aggregateDailyPunishmentCounts(
                server, usernames, staffId, startDate);
        for (IdCountResult result : punishmentResults) {
            activityByDate.put(result.id(),
                new StaffDetailsResponse.DailyActivity(result.id(), result.count(), 0, 0));
        }

        List<IdCountResult> ticketResults =
            auditRepository.aggregateDailyTicketResponseCounts(
                server, usernames.get(0), startDate);
        for (IdCountResult result : ticketResults) {
            StaffDetailsResponse.DailyActivity existing = activityByDate.get(result.id());
            if (existing != null) {
                activityByDate.put(result.id(), new StaffDetailsResponse.DailyActivity(
                    result.id(), existing.punishments(), result.count(), existing.evidence()));
            } else {
                activityByDate.put(result.id(),
                    new StaffDetailsResponse.DailyActivity(result.id(), 0, result.count(), 0));
            }
        }

        return activityByDate.values()
            .stream()
            .sorted(Comparator.comparing(StaffDetailsResponse.DailyActivity::date))
            .toList();
    }

    private List<StaffDetailsResponse.PunishmentTypeBreakdown> getPunishmentTypeBreakdown(
        Server server, List<String> usernames, String staffId, Date startDate) {
        List<StaffDetailsResponse.PunishmentTypeBreakdown> breakdown = new ArrayList<>();
        List<OrdinalCountResult> results =
            auditRepository.aggregatePunishmentTypeBreakdown(
                server, usernames, staffId, startDate);

        for (OrdinalCountResult result : results) {
            String typeName = punishmentTypeService.getPunishmentTypeName(
                server, result.id() != null ? result.id() : 0);
            breakdown.add(new StaffDetailsResponse.PunishmentTypeBreakdown(typeName, result.count()));
        }
        return breakdown;
    }

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
        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        List<Document> rows = auditRepository.aggregatePunishmentRows(server);
        Map<String, String> resolvedIssuers = resolveIssuerNames(server, rows);

        boolean filterActive = "active".equalsIgnoreCase(statusFilter);
        boolean filterInactive = "inactive".equalsIgnoreCase(statusFilter);

        List<ActivePunishmentResponse> results = new ArrayList<>();
        for (Document row : rows) {
            Punishment punishment = reconstructPunishment(row);
            boolean active = statusCalculator.isPunishmentActive(punishment);

            if (filterActive && !active) {
                continue;
            }
            if (filterInactive && active) {
                continue;
            }

            results.add(mapToActivePunishmentResponse(
                server, row, punishment, active, punishmentTypes, resolvedIssuers));
        }

        return results;
    }

    private ActivePunishmentResponse mapToActivePunishmentResponse(
        Server server, Document row, Punishment punishment, boolean active,
        List<PunishmentType> punishmentTypes, Map<String, String> resolvedIssuers) {
        int typeOrdinal = row.getInteger("typeOrdinal", 0);
        String typeName = punishmentTypeService.getPunishmentTypeName(server, typeOrdinal);
        String category = punishmentTypes.stream()
            .filter(type -> type.getOrdinal() == typeOrdinal)
            .findFirst()
            .map(type -> type.getCategory() != null ? type.getCategory() : "Administrative")
            .orElse("Administrative");

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
            extractPlayerNameFromDoc(row),
            typeName,
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
        punishment.setId(doc.getString("punishmentId"));
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
            throw new IllegalArgumentException("This punishment cannot be rolled back");
        }

        AuditLog rollbackLog = AuditLog.builder()
            .created(new Date())
            .level("moderation")
            .source(performerUsername)
            .description("Rolled back " + extractPunishmentType(punishment.getDescription())
                         + " for "
                         + (metadata != null ? metadata.get("playerName") : "unknown player"))
            .metadata(Map.of(
                "originalPunishmentId", punishmentId,
                "rollbackReason", reason != null ? reason : "Admin rollback",
                "originalPunishment", Map.of(
                    "type", extractPunishmentType(punishment.getDescription()),
                    "player", metadata != null
                              ? metadata.getOrDefault("playerName", "") : "",
                    "staff", punishment.getSource(),
                    "originalReason", metadata != null
                                      ? metadata.getOrDefault("reason", "") : ""
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
        List<String> allowedTables =
            List.of("players", "tickets", "staff", "punishments", "logs", "settings");
        if (!allowedTables.contains(table)) {
            throw new IllegalArgumentException("Invalid table name");
        }

        String collectionName = getCollectionName(table);
        List<Document> documents = auditRepository.readTable(server, collectionName, limit, skip);
        long total = auditRepository.countCollection(server, collectionName);

        return Map.of(
            "data", documents,
            "total", total,
            "limit", limit,
            "skip", skip
        );
    }

    private String getCollectionName(String table) {
        return switch (table) {
            case "players" -> CollectionName.PLAYERS;
            case "tickets" -> CollectionName.TICKETS;
            case "staff" -> CollectionName.STAFF;
            case "logs", "punishments" -> CollectionName.LOGS;
            case "settings" -> CollectionName.SETTINGS;
            default -> throw new ValidationException("Unknown table: " + table);
        };
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
            Map<String, Object> rollbackModification = new HashMap<>();
            rollbackModification.put("type", "ROLLBACK");
            rollbackModification.put("timestamp", now);
            rollbackModification.put("performedBy", performerUsername);
            rollbackModification.put("reason", reason);

            int rollbackCount = 0;
            for (Document player : players) {
                rollbackCount += applyRollbackToPlayer(
                    server, player, staffUsername, staffId,
                    startDate, endDate, reason, performerUsername,
                    rollbackModification, now);
            }
            return rollbackCount;
        } catch (Exception e) {
            log.error("Error during bulk rollback for staff {}: {}",
                staffUsername, e.getMessage());
            throw new RuntimeException("Failed to rollback punishments: " + e.getMessage());
        }
    }

    private int applyRollbackToPlayer(
        Server server, Document player, String staffUsername, String staffId,
        Date startDate, Date endDate, String reason, String performerUsername,
        Map<String, Object> rollbackModification, Date now) {
        String playerId = player.getString("_id");
        List<Document> punishments = player.getList("punishments", Document.class);
        if (punishments == null) {
            return 0;
        }

        String playerName = extractPlayerNameFromDoc(player);
        int count = 0;

        for (Document punishment : punishments) {
            if (!matchesIssuer(punishment, staffUsername, staffId)) {
                continue;
            }
            if (!isWithinDateRange(punishment.getDate("issued"), startDate, endDate)) {
                continue;
            }
            if (hasModificationType(punishment, "ROLLBACK")) {
                continue;
            }

            String punishmentId = punishment.getString("_id");
            auditRepository.appendRollbackModification(
                server, playerId, punishmentId, rollbackModification);

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
}
