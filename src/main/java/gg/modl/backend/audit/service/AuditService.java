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

        // First, get all staff members
        List<Staff> allStaff = template.findAll(Staff.class, CollectionName.STAFF);

        // Aggregate log activity by source (username)
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

        List<Document> logResults = template.aggregate(aggregation, CollectionName.LOGS, Document.class).getMappedResults();

        // Create a map of username -> log activity
        Map<String, Document> activityByUsername = new HashMap<>();
        for (Document doc : logResults) {
            String username = doc.getString("_id");
            if (username != null) {
                activityByUsername.put(username.toLowerCase(), doc);
            }
        }

        // Also count ticket responses from the tickets collection
        Map<String, Integer> ticketResponsesByStaff = countTicketResponsesByStaff(template, startDate);

        // Also count punishments issued from punishments/logs
        Map<String, Integer> punishmentsByStaff = countPunishmentsByStaff(template, startDate);

        List<StaffPerformanceResponse> performanceList = new ArrayList<>();

        for (Staff staff : allStaff) {
            String username = staff.getUsername();
            if (username == null) continue;

            Document activity = activityByUsername.get(username.toLowerCase());

            int totalActions = activity != null ? activity.getInteger("totalActions", 0) : 0;
            int ticketActions = ticketResponsesByStaff.getOrDefault(username.toLowerCase(), 0);
            int moderationActions = punishmentsByStaff.getOrDefault(username.toLowerCase(), 0);
            Date lastActive = activity != null ? activity.getDate("lastActive") : staff.getUpdatedAt();

            // Add ticket and moderation actions to total if they weren't counted in logs
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

        // Sort by total actions descending
        performanceList.sort((a, b) -> Integer.compare(b.totalActions(), a.totalActions()));

        return performanceList;
    }

    private Map<String, Integer> countTicketResponsesByStaff(MongoTemplate template, Date startDate) {
        Map<String, Integer> counts = new HashMap<>();

        try {
            // Count staff replies in tickets
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("replies"),
                    Aggregation.match(Criteria.where("replies.staff").is(true)
                            .and("replies.created").gte(startDate)),
                    Aggregation.group("replies.name").count().as("count")
            );

            List<Document> results = template.aggregate(aggregation, CollectionName.TICKETS, Document.class).getMappedResults();
            for (Document doc : results) {
                String name = doc.getString("_id");
                if (name != null) {
                    counts.put(name.toLowerCase(), doc.getInteger("count", 0));
                }
            }
        } catch (Exception e) {
            log.debug("Error counting ticket responses: {}", e.getMessage());
        }

        return counts;
    }

    private Map<String, Integer> countPunishmentsByStaff(MongoTemplate template, Date startDate) {
        Map<String, Integer> counts = new HashMap<>();

        try {
            // Count punishments by issuerName from the players collection (punishments are embedded)
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("punishments"),
                    Aggregation.match(Criteria.where("punishments.issued").gte(startDate)),
                    Aggregation.group("punishments.issuerName").count().as("count")
            );

            List<Document> results = template.aggregate(aggregation, CollectionName.PLAYERS, Document.class).getMappedResults();
            for (Document doc : results) {
                String issuerName = doc.getString("_id");
                if (issuerName != null) {
                    counts.put(issuerName.toLowerCase(), doc.getInteger("count", 0));
                }
            }
        } catch (Exception e) {
            log.debug("Error counting punishments: {}", e.getMessage());
        }

        return counts;
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
        List<StaffDetailsResponse.PunishmentDetail> details = new ArrayList<>();

        try {
            // Query players collection and unwind punishments issued by this staff member
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("punishments"),
                    Aggregation.match(Criteria.where("punishments.issuerName").regex("^" + Pattern.quote(username) + "$", "i")
                            .and("punishments.issued").gte(startDate)),
                    Aggregation.sort(Sort.Direction.DESC, "punishments.issued"),
                    Aggregation.limit(50),
                    Aggregation.project()
                            .and("punishments._id").as("punishmentId")
                            .and("_id").as("playerId")
                            .and("punishments.type_ordinal").as("typeOrdinal")
                            .and("punishments.issued").as("issued")
                            .and("punishments.started").as("started")
                            .and("punishments.data.reason").as("reason")
                            .and("punishments.data.duration").as("duration")
                            .and("punishments.modifications").as("modifications")
                            .and("usernames").as("usernames")
            );

            List<Document> results = template.aggregate(aggregation, CollectionName.PLAYERS, Document.class).getMappedResults();

            for (Document doc : results) {
                String punishmentId = doc.getString("punishmentId");
                String playerId = doc.getString("playerId");
                int typeOrdinal = doc.getInteger("typeOrdinal", 0);
                Date issued = doc.getDate("issued");
                String reason = doc.getString("reason");
                Object durationObj = doc.get("duration");
                String duration = durationObj != null ? durationObj.toString() : null;

                // Get player name from first username entry (most recent)
                String playerName = "Unknown";
                List<?> usernames = doc.getList("usernames", Document.class);
                if (usernames != null && !usernames.isEmpty()) {
                    Document firstUsername = (Document) usernames.get(0);
                    playerName = firstUsername.getString("username");
                    if (playerName == null) playerName = "Unknown";
                }

                // Check if punishment is active (not removed/expired)
                boolean active = isPunishmentActive(doc);
                boolean rolledBack = isPunishmentRolledBack(doc);

                // Map type ordinal to type name
                String typeName = getTypeNameFromOrdinal(typeOrdinal);

                details.add(new StaffDetailsResponse.PunishmentDetail(
                        punishmentId,
                        playerId,
                        playerName,
                        typeName,
                        reason != null ? reason : "No reason provided",
                        duration,
                        issued,
                        active,
                        rolledBack
                ));
            }
        } catch (Exception e) {
            log.debug("Error fetching punishment details for {}: {}", username, e.getMessage());
        }

        return details;
    }

    private List<StaffDetailsResponse.TicketDetail> getTicketDetails(MongoTemplate template, String username, Date startDate) {
        List<StaffDetailsResponse.TicketDetail> details = new ArrayList<>();

        try {
            // Find tickets where this staff member has replied
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("replies"),
                    Aggregation.match(Criteria.where("replies.staff").is(true)
                            .and("replies.name").regex("^" + Pattern.quote(username) + "$", "i")
                            .and("replies.created").gte(startDate)),
                    Aggregation.sort(Sort.Direction.DESC, "replies.created"),
                    Aggregation.group("_id")
                            .first("subject").as("subject")
                            .first("category").as("category")
                            .first("status").as("status")
                            .first("created").as("ticketCreated")
                            .max("replies.created").as("lastActivity")
                            .min("replies.created").as("firstReply"),
                    Aggregation.limit(50)
            );

            List<Document> results = template.aggregate(aggregation, CollectionName.TICKETS, Document.class).getMappedResults();

            for (Document doc : results) {
                String ticketId = doc.getString("_id");
                String subject = doc.getString("subject");
                String category = doc.getString("category");
                String status = doc.getString("status");
                Date lastActivity = doc.getDate("lastActivity");
                Date ticketCreated = doc.getDate("ticketCreated");
                Date firstReply = doc.getDate("firstReply");

                // Calculate response time in minutes (time from ticket creation to first staff reply)
                int responseTime = 0;
                if (ticketCreated != null && firstReply != null) {
                    long diffMs = firstReply.getTime() - ticketCreated.getTime();
                    responseTime = (int) (diffMs / (1000 * 60)); // Convert to minutes
                }

                details.add(new StaffDetailsResponse.TicketDetail(
                        ticketId,
                        subject != null ? subject : "No Subject",
                        category != null ? category : "General",
                        status != null ? status : "Unknown",
                        lastActivity,
                        responseTime
                ));
            }
        } catch (Exception e) {
            log.debug("Error fetching ticket details for {}: {}", username, e.getMessage());
        }

        return details;
    }

    private List<StaffDetailsResponse.DailyActivity> getDailyActivity(MongoTemplate template, String username, Date startDate) {
        Map<String, StaffDetailsResponse.DailyActivity> activityByDate = new HashMap<>();

        try {
            // Get daily punishment counts
            Aggregation punishmentAgg = Aggregation.newAggregation(
                    Aggregation.unwind("punishments"),
                    Aggregation.match(Criteria.where("punishments.issuerName").regex("^" + Pattern.quote(username) + "$", "i")
                            .and("punishments.issued").gte(startDate)),
                    Aggregation.project()
                            .andExpression("dateToString('%Y-%m-%d', punishments.issued)").as("date"),
                    Aggregation.group("date").count().as("count")
            );

            List<Document> punishmentResults = template.aggregate(punishmentAgg, CollectionName.PLAYERS, Document.class).getMappedResults();
            for (Document doc : punishmentResults) {
                String date = doc.getString("_id");
                int count = doc.getInteger("count", 0);
                activityByDate.put(date, new StaffDetailsResponse.DailyActivity(date, count, 0, 0));
            }

            // Get daily ticket response counts
            Aggregation ticketAgg = Aggregation.newAggregation(
                    Aggregation.unwind("replies"),
                    Aggregation.match(Criteria.where("replies.staff").is(true)
                            .and("replies.name").regex("^" + Pattern.quote(username) + "$", "i")
                            .and("replies.created").gte(startDate)),
                    Aggregation.project()
                            .andExpression("dateToString('%Y-%m-%d', replies.created)").as("date"),
                    Aggregation.group("date").count().as("count")
            );

            List<Document> ticketResults = template.aggregate(ticketAgg, CollectionName.TICKETS, Document.class).getMappedResults();
            for (Document doc : ticketResults) {
                String date = doc.getString("_id");
                int count = doc.getInteger("count", 0);
                StaffDetailsResponse.DailyActivity existing = activityByDate.get(date);
                if (existing != null) {
                    activityByDate.put(date, new StaffDetailsResponse.DailyActivity(date, existing.punishments(), count, existing.evidence()));
                } else {
                    activityByDate.put(date, new StaffDetailsResponse.DailyActivity(date, 0, count, 0));
                }
            }
        } catch (Exception e) {
            log.debug("Error fetching daily activity for {}: {}", username, e.getMessage());
        }

        return activityByDate.values().stream()
                .sorted(Comparator.comparing(StaffDetailsResponse.DailyActivity::date))
                .toList();
    }

    private List<StaffDetailsResponse.PunishmentTypeBreakdown> getPunishmentTypeBreakdown(MongoTemplate template, String username, Date startDate) {
        List<StaffDetailsResponse.PunishmentTypeBreakdown> breakdown = new ArrayList<>();

        try {
            // Aggregate punishments by type_ordinal
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("punishments"),
                    Aggregation.match(Criteria.where("punishments.issuerName").regex("^" + Pattern.quote(username) + "$", "i")
                            .and("punishments.issued").gte(startDate)),
                    Aggregation.group("punishments.type_ordinal").count().as("count"),
                    Aggregation.sort(Sort.Direction.DESC, "count")
            );

            List<Document> results = template.aggregate(aggregation, CollectionName.PLAYERS, Document.class).getMappedResults();

            for (Document doc : results) {
                Integer typeOrdinal = doc.getInteger("_id");
                int count = doc.getInteger("count", 0);
                String typeName = getTypeNameFromOrdinal(typeOrdinal != null ? typeOrdinal : 0);

                breakdown.add(new StaffDetailsResponse.PunishmentTypeBreakdown(typeName, count));
            }
        } catch (Exception e) {
            log.debug("Error fetching punishment type breakdown for {}: {}", username, e.getMessage());
        }

        return breakdown;
    }

    private boolean isPunishmentActive(Document doc) {
        // Check modifications for removal
        List<?> modifications = doc.getList("modifications", Document.class);
        if (modifications != null) {
            for (Object mod : modifications) {
                if (mod instanceof Document modDoc) {
                    String type = modDoc.getString("type");
                    if ("REMOVE".equalsIgnoreCase(type) || "REVOKE".equalsIgnoreCase(type)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isPunishmentRolledBack(Document doc) {
        List<?> modifications = doc.getList("modifications", Document.class);
        if (modifications != null) {
            for (Object mod : modifications) {
                if (mod instanceof Document modDoc) {
                    String type = modDoc.getString("type");
                    if ("ROLLBACK".equalsIgnoreCase(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String getTypeNameFromOrdinal(int ordinal) {
        // Common punishment type ordinals - these should ideally come from settings
        return switch (ordinal) {
            case 0 -> "Warning";
            case 1 -> "Mute";
            case 2 -> "Kick";
            case 3 -> "Temporary Ban";
            case 4 -> "Permanent Ban";
            case 5 -> "Blacklist";
            default -> "Type " + ordinal;
        };
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
