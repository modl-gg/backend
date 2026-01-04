package gg.modl.backend.audit.service;

import gg.modl.backend.audit.data.AuditLog;
import gg.modl.backend.audit.dto.response.PunishmentAuditResponse;
import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public List<StaffPerformanceResponse> getStaffPerformance(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("created").gte(startDate).and("source").ne("system")),
                Aggregation.group("source")
                        .count().as("totalActions")
                        .sum(ConditionalOperators.when(Criteria.where("description").regex(Pattern.compile("ticket", Pattern.CASE_INSENSITIVE))).then(1).otherwise(0)).as("ticketActions")
                        .sum(ConditionalOperators.when(new Criteria().orOperator(
                                Criteria.where("level").is("moderation"),
                                Criteria.where("description").regex(Pattern.compile("ban|mute|kick|punishment", Pattern.CASE_INSENSITIVE))
                        )).then(1).otherwise(0)).as("moderationActions")
                        .max("created").as("lastActive"),
                Aggregation.sort(Sort.Direction.DESC, "totalActions")
        );

        List<Document> results = template.aggregate(aggregation, CollectionName.LOGS, Document.class).getMappedResults();

        List<StaffPerformanceResponse> performanceList = new ArrayList<>();

        for (Document doc : results) {
            String username = doc.getString("_id");

            Query staffQuery = Query.query(Criteria.where("username").is(username));
            Staff staff = template.findOne(staffQuery, Staff.class, CollectionName.STAFF);
            String role = staff != null && staff.getRole() != null ? staff.getRole() : "User";

            performanceList.add(new StaffPerformanceResponse(
                    doc.getString("_id"),
                    username,
                    role,
                    doc.getInteger("totalActions", 0),
                    doc.getInteger("ticketActions", 0),
                    doc.getInteger("moderationActions", 0),
                    60,
                    doc.getDate("lastActive")
            ));
        }

        return performanceList;
    }

    public StaffDetailsResponse getStaffDetails(Server server, String username, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        List<StaffDetailsResponse.PunishmentDetail> punishments = getPunishmentDetails(template, username, startDate);
        List<StaffDetailsResponse.TicketDetail> tickets = getTicketDetails(template, username, startDate);
        List<StaffDetailsResponse.DailyActivity> dailyActivity = getDailyActivity(template, username, startDate);
        List<StaffDetailsResponse.PunishmentTypeBreakdown> typeBreakdown = getPunishmentTypeBreakdown(template, username, startDate);

        long evidenceUploads = countEvidenceUploads(template, username, startDate);

        int avgResponseTime = tickets.isEmpty() ? 0 :
                (int) tickets.stream().mapToInt(StaffDetailsResponse.TicketDetail::responseTime).average().orElse(0);

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

    public List<PunishmentAuditResponse> getPunishments(Server server, int limit, boolean canRollbackOnly) {
        MongoTemplate template = getTemplate(server);
        Date thirtyDaysAgo = getStartDate("30d");

        Criteria criteria = Criteria.where("created").gte(thirtyDaysAgo)
                .orOperator(
                        Criteria.where("level").is("moderation"),
                        Criteria.where("description").regex(Pattern.compile("ban|mute|kick|warn", Pattern.CASE_INSENSITIVE))
                );

        if (canRollbackOnly) {
            criteria = criteria.and("metadata.canRollback").ne(false);
        }

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "created"))
                .limit(limit);

        List<AuditLog> logs = template.find(query, AuditLog.class, CollectionName.LOGS);

        return logs.stream().map(log -> {
            Map<String, Object> metadata = log.getMetadata() != null ? log.getMetadata() : Collections.emptyMap();

            return new PunishmentAuditResponse(
                    log.getId(),
                    extractPunishmentType(log.getDescription()),
                    getStringFromMetadata(metadata, "playerId", "unknown"),
                    getStringFromMetadata(metadata, "playerName", extractPlayerName(log.getDescription())),
                    getStringFromMetadata(metadata, "staffId", log.getSource()),
                    log.getSource(),
                    getStringFromMetadata(metadata, "reason", extractReason(log.getDescription())),
                    getStringFromMetadata(metadata, "duration", null),
                    log.getCreated(),
                    !Boolean.FALSE.equals(metadata.get("canRollback"))
            );
        }).toList();
    }

    public boolean rollbackPunishment(Server server, String punishmentId, String reason, String performerUsername) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(punishmentId));
        AuditLog punishment = template.findOne(query, AuditLog.class, CollectionName.LOGS);

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
                .description("Rolled back " + extractPunishmentType(punishment.getDescription()) +
                        " for " + (metadata != null ? metadata.get("playerName") : "unknown player"))
                .metadata(Map.of(
                        "originalPunishmentId", punishmentId,
                        "rollbackReason", reason != null ? reason : "Admin rollback",
                        "originalPunishment", Map.of(
                                "type", extractPunishmentType(punishment.getDescription()),
                                "player", metadata != null ? metadata.getOrDefault("playerName", "") : "",
                                "staff", punishment.getSource(),
                                "originalReason", metadata != null ? metadata.getOrDefault("reason", "") : ""
                        )
                ))
                .build();

        template.save(rollbackLog, CollectionName.LOGS);

        Update update = new Update()
                .set("metadata.rolledBack", true)
                .set("metadata.rollbackDate", new Date())
                .set("metadata.rollbackBy", performerUsername);

        template.updateFirst(query, update, AuditLog.class, CollectionName.LOGS);

        return true;
    }

    public Map<String, Object> getDatabaseTable(Server server, String table, int limit, int skip) {
        MongoTemplate template = getTemplate(server);

        List<String> allowedTables = List.of("players", "tickets", "staff", "punishments", "logs", "settings");
        if (!allowedTables.contains(table)) {
            throw new IllegalArgumentException("Invalid table name");
        }

        String collectionName = getCollectionName(table);

        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "_id"))
                .skip(skip)
                .limit(limit);

        List<Document> documents = template.find(query, Document.class, collectionName);
        long total = template.count(new Query(), collectionName);

        return Map.of(
                "data", documents,
                "total", total,
                "limit", limit,
                "skip", skip
        );
    }

    private List<StaffDetailsResponse.PunishmentDetail> getPunishmentDetails(MongoTemplate template, String username, Date startDate) {
        return Collections.emptyList();
    }

    private List<StaffDetailsResponse.TicketDetail> getTicketDetails(MongoTemplate template, String username, Date startDate) {
        return Collections.emptyList();
    }

    private List<StaffDetailsResponse.DailyActivity> getDailyActivity(MongoTemplate template, String username, Date startDate) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("source").is(username).and("created").gte(startDate)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', created)").as("date")
                        .and("description").as("description")
                        .and("level").as("level"),
                Aggregation.group("date")
                        .count().as("total")
                        .sum(ConditionalOperators.when(Criteria.where("level").is("moderation")).then(1).otherwise(0)).as("punishments")
        );

        List<Document> results = template.aggregate(aggregation, CollectionName.LOGS, Document.class).getMappedResults();

        return results.stream()
                .map(doc -> new StaffDetailsResponse.DailyActivity(
                        doc.getString("_id"),
                        doc.getInteger("punishments", 0),
                        doc.getInteger("total", 0) - doc.getInteger("punishments", 0),
                        0
                ))
                .sorted(Comparator.comparing(StaffDetailsResponse.DailyActivity::date))
                .toList();
    }

    private List<StaffDetailsResponse.PunishmentTypeBreakdown> getPunishmentTypeBreakdown(MongoTemplate template, String username, Date startDate) {
        return Collections.emptyList();
    }

    private long countEvidenceUploads(MongoTemplate template, String username, Date startDate) {
        Query query = Query.query(
                Criteria.where("source").is(username)
                        .and("created").gte(startDate)
                        .orOperator(
                                Criteria.where("description").regex(Pattern.compile("evidence|upload|file", Pattern.CASE_INSENSITIVE)),
                                Criteria.where("level").is("info").and("description").regex(Pattern.compile("uploaded|attachment", Pattern.CASE_INSENSITIVE))
                        )
        );
        return template.count(query, AuditLog.class, CollectionName.LOGS);
    }

    private Date getStartDate(String period) {
        long now = System.currentTimeMillis();
        long daysInMs = 24 * 60 * 60 * 1000L;

        return switch (period) {
            case "7d" -> new Date(now - 7 * daysInMs);
            case "90d" -> new Date(now - 90 * daysInMs);
            default -> new Date(now - 30 * daysInMs);
        };
    }

    private String getCollectionName(String table) {
        return switch (table) {
            case "players" -> CollectionName.PLAYERS;
            case "tickets" -> CollectionName.TICKETS;
            case "staff" -> CollectionName.STAFF;
            case "logs", "punishments" -> CollectionName.LOGS;
            case "settings" -> CollectionName.SETTINGS;
            default -> table;
        };
    }

    private String extractPunishmentType(String description) {
        if (description == null) return "Unknown";
        String lower = description.toLowerCase();
        if (lower.contains("ban")) return "Ban";
        if (lower.contains("mute")) return "Mute";
        if (lower.contains("kick")) return "Kick";
        if (lower.contains("warn")) return "Warn";
        return "Unknown";
    }

    private String extractPlayerName(String description) {
        return "Unknown";
    }

    private String extractReason(String description) {
        return description;
    }

    private String getStringFromMetadata(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
