package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.dto.response.AuditLogsAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.analytics.dto.response.PlayerActivityResponse;
import gg.modl.backend.analytics.dto.response.PunishmentAnalyticsResponse;
import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private static final String ANALYTICS_TIME_ZONE = TimeZone.getDefault().getID();

    private final DynamicMongoTemplateProvider mongoProvider;
    private final PunishmentTypeService punishmentTypeService;

    public OverviewResponse getOverview(Server server) {
        MongoTemplate template = getTemplate(server);

        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        Date thirtyDaysAgo = new Date(now - thirtyDaysMs);
        Date sixtyDaysAgo = new Date(now - 2 * thirtyDaysMs);

        long totalTickets = template.count(new Query(), Ticket.class, CollectionName.TICKETS);
        long totalPlayers = template.count(new Query(), Player.class, CollectionName.PLAYERS);
        long totalStaff = template.count(new Query(), Staff.class, CollectionName.STAFF);
        long activeTickets = template.count(Query.query(Criteria.where("status").is("Open")), Ticket.class, CollectionName.TICKETS);

        long recentTickets = template.count(Query.query(Criteria.where("created").gte(thirtyDaysAgo)), Ticket.class, CollectionName.TICKETS);
        long prevTickets = template.count(Query.query(Criteria.where("created").gte(sixtyDaysAgo).lt(thirtyDaysAgo)), Ticket.class, CollectionName.TICKETS);

        int ticketChange = prevTickets > 0 ? (int) Math.round(((double) (recentTickets - prevTickets) / prevTickets) * 100) : 0;
        int playerChange = 0;

        return new OverviewResponse(
                totalTickets,
                totalPlayers,
                totalStaff,
                activeTickets,
                ticketChange,
                playerChange
        );
    }

    public TicketAnalyticsResponse getTicketAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        // Build criteria - include date filter only if startDate is not null
        Criteria baseCriteria = Criteria.where("status").ne("Unfinished");
        if (startDate != null) {
            baseCriteria = baseCriteria.and("created").gte(startDate);
        }

        Aggregation statusAgg = Aggregation.newAggregation(
                Aggregation.match(baseCriteria),
                Aggregation.group("status").count().as("count")
        );
        List<Document> statusResults = template.aggregate(statusAgg, CollectionName.TICKETS, Document.class).getMappedResults();
        List<TicketAnalyticsResponse.StatusCount> byStatus = statusResults.stream()
                .map(doc -> new TicketAnalyticsResponse.StatusCount(doc.getString("_id"), doc.getInteger("count", 0)))
                .toList();

        Aggregation categoryAgg = Aggregation.newAggregation(
                Aggregation.match(baseCriteria),
                Aggregation.group("type").count().as("count")
        );
        List<Document> categoryResults = template.aggregate(categoryAgg, CollectionName.TICKETS, Document.class).getMappedResults();
        List<TicketAnalyticsResponse.CategoryCount> byCategory = categoryResults.stream()
                .map(doc -> new TicketAnalyticsResponse.CategoryCount(normalizeCategory(doc.getString("_id")), doc.getInteger("count", 0)))
                .toList();

        // Generate daily ticket counts
        Map<String, Integer> dailyCountMap = new TreeMap<>();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd");

        Query ticketQuery = Query.query(baseCriteria);
        List<Ticket> tickets = template.find(ticketQuery, Ticket.class, CollectionName.TICKETS);

        for (Ticket ticket : tickets) {
            if (ticket.getCreated() != null) {
                String dateKey = java.time.Instant.ofEpochMilli(ticket.getCreated().getTime())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .format(dateFormatter);
                dailyCountMap.merge(dateKey, 1, Integer::sum);
            }
        }

        List<TicketAnalyticsResponse.DailyTicket> dailyTickets = dailyCountMap.entrySet().stream()
                .map(e -> new TicketAnalyticsResponse.DailyTicket(e.getKey(), e.getValue()))
                .toList();

        List<TicketAnalyticsResponse.CategoryResolutionTime> avgResolution = Collections.emptyList();

        return new TicketAnalyticsResponse(byStatus, byCategory, avgResolution, dailyTickets);
    }

    public PunishmentAnalyticsResponse getPunishmentAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        List<Document> pipeline = buildPunishmentAnalyticsPipeline(startDate);
        List<Document> aggregateResults = template.getCollection(CollectionName.PLAYERS)
                .aggregate(pipeline)
                .into(new ArrayList<>());

        if (aggregateResults.isEmpty()) {
            return new PunishmentAnalyticsResponse(List.of(), List.of(), List.of(), List.of());
        }

        Document facetResults = aggregateResults.get(0);
        Map<Integer, String> punishmentTypeNames = resolvePunishmentTypeNames(server);

        List<PunishmentAnalyticsResponse.TypeCount> byType = toDocumentList(facetResults.get("byType")).stream()
                .map(doc -> {
                    Object rawTypeOrdinal = doc.get("_id");
                    Integer typeOrdinal = rawTypeOrdinal instanceof Number number ? number.intValue() : null;
                    String typeName = typeOrdinal != null
                            ? punishmentTypeNames.getOrDefault(typeOrdinal, "Unknown")
                            : "Unknown";
                    return new PunishmentAnalyticsResponse.TypeCount(typeName, toInt(doc.get("count")));
                })
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .toList();

        Map<String, Integer> staffCountMap = new HashMap<>();
        for (Document document : toDocumentList(facetResults.get("byStaff"))) {
            String staffName = normalizeStaffName(document.get("_id"));
            staffCountMap.merge(staffName, toInt(document.get("count")), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.StaffPunishment> byStaff = staffCountMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .map(entry -> new PunishmentAnalyticsResponse.StaffPunishment(entry.getKey(), entry.getValue()))
                .toList();

        Map<String, Integer> dailyPunishmentMap = new LinkedHashMap<>();
        for (Document document : toDocumentList(facetResults.get("daily"))) {
            String dayLabel = formatPunishmentDay(document.getString("_id"));
            dailyPunishmentMap.merge(dayLabel, toInt(document.get("count")), Integer::sum);
        }
        List<PunishmentAnalyticsResponse.DailyPunishment> dailyPunishments = dailyPunishmentMap.entrySet().stream()
                .map(entry -> new PunishmentAnalyticsResponse.DailyPunishment(entry.getKey(), entry.getValue()))
                .toList();

        // Severity is not represented in the stored punishment analytics schema today.
        return new PunishmentAnalyticsResponse(byType, List.of(), dailyPunishments, byStaff);
    }

    public AuditLogsAnalyticsResponse getAuditLogsAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        // Build criteria - include date filter only if startDate is not null
        Criteria logCriteria = new Criteria();
        if (startDate != null) {
            logCriteria = Criteria.where("created").gte(startDate);
        }

        // Aggregate logs by level
        Aggregation levelAgg = Aggregation.newAggregation(
                Aggregation.match(logCriteria),
                Aggregation.group("level").count().as("count")
        );
        List<Document> levelResults = template.aggregate(levelAgg, CollectionName.LOGS, Document.class).getMappedResults();
        List<AuditLogsAnalyticsResponse.LevelCount> byLevel = levelResults.stream()
                .map(doc -> new AuditLogsAnalyticsResponse.LevelCount(
                        doc.getString("_id") != null ? doc.getString("_id") : "unknown",
                        doc.getInteger("count", 0)
                ))
                .toList();

        // Generate hourly trend for the last 24 hours
        List<AuditLogsAnalyticsResponse.HourlyCount> hourlyTrend = new ArrayList<>();
        long now = System.currentTimeMillis();
        long hourMs = 60 * 60 * 1000L;

        for (int i = 23; i >= 0; i--) {
            Date hourStart = new Date(now - (i + 1) * hourMs);
            Date hourEnd = new Date(now - i * hourMs);

            long count = template.count(
                    Query.query(Criteria.where("created").gte(hourStart).lt(hourEnd)),
                    CollectionName.LOGS
            );

            java.time.LocalTime time = java.time.Instant.ofEpochMilli(hourEnd.getTime())
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime();
            String hourLabel = String.format("%02d:00", time.getHour());

            hourlyTrend.add(new AuditLogsAnalyticsResponse.HourlyCount(hourLabel, (int) count));
        }

        return new AuditLogsAnalyticsResponse(byLevel, hourlyTrend);
    }

    public PlayerActivityResponse getPlayerActivityAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        List<Player> players = template.findAll(Player.class, CollectionName.PLAYERS);

        // Calculate new players trend (players whose first IP login is within the period)
        Map<String, Integer> dailyNewPlayers = new TreeMap<>();
        Map<String, Integer> countryLogins = new HashMap<>();
        int proxyCount = 0;
        int hostingCount = 0;

        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd");

        for (Player player : players) {
            if (player.getIpAddresses() == null || player.getIpAddresses().isEmpty()) {
                continue;
            }

            // Find the earliest first login date across all IP entries
            Date earliestFirstLogin = player.getIpAddresses().stream()
                    .map(ip -> ip.getFirstLogin())
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);

            // Count as new player if their first login is within the period (or all time if startDate is null)
            if (earliestFirstLogin != null && (startDate == null || earliestFirstLogin.after(startDate))) {
                String dateKey = java.time.Instant.ofEpochMilli(earliestFirstLogin.getTime())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .format(dateFormatter);
                dailyNewPlayers.merge(dateKey, 1, Integer::sum);
            }

            // Process each IP entry for country stats and suspicious activity
            for (var ipEntry : player.getIpAddresses()) {
                // Count logins by country (only for logins within period, or all if startDate is null)
                if (ipEntry.getCountry() != null && !ipEntry.getCountry().isEmpty()) {
                    long loginsInPeriod = ipEntry.getLogins() != null
                            ? (startDate == null ? ipEntry.getLogins().size() : ipEntry.getLogins().stream().filter(d -> d.after(startDate)).count())
                            : 0;
                    boolean firstLoginInPeriod = ipEntry.getFirstLogin() != null && (startDate == null || ipEntry.getFirstLogin().after(startDate));
                    if (loginsInPeriod > 0 || firstLoginInPeriod) {
                        countryLogins.merge(ipEntry.getCountry(), (int) Math.max(1, loginsInPeriod), Integer::sum);
                    }
                }

                // Check for logins within period for suspicious activity counting (or all if startDate is null)
                boolean hasRecentLogin = startDate == null
                        || (ipEntry.getFirstLogin() != null && ipEntry.getFirstLogin().after(startDate))
                        || (ipEntry.getLogins() != null && ipEntry.getLogins().stream().anyMatch(d -> d.after(startDate)));

                if (hasRecentLogin) {
                    if (ipEntry.isProxy()) {
                        proxyCount++;
                    }
                    if (ipEntry.isHosting()) {
                        hostingCount++;
                    }
                }
            }
        }

        // Convert to response format
        List<PlayerActivityResponse.DailyCount> newPlayersTrend = dailyNewPlayers.entrySet().stream()
                .map(e -> new PlayerActivityResponse.DailyCount(e.getKey(), e.getValue()))
                .toList();

        List<PlayerActivityResponse.CountryCount> loginsByCountry = countryLogins.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(20)
                .map(e -> new PlayerActivityResponse.CountryCount(e.getKey(), e.getValue()))
                .toList();

        PlayerActivityResponse.SuspiciousActivity suspiciousActivity =
                new PlayerActivityResponse.SuspiciousActivity(proxyCount, hostingCount);

        return new PlayerActivityResponse(newPlayersTrend, loginsByCountry, suspiciousActivity);
    }

    private List<Document> buildPunishmentAnalyticsPipeline(Date startDate) {
        List<Document> pipeline = new ArrayList<>();
        if (startDate != null) {
            // Pre-filter documents so Mongo can leverage the multikey issued index before unwind.
            pipeline.add(new Document("$match", new Document("punishments.issued", new Document("$gte", startDate))));
        }

        pipeline.add(new Document("$unwind", "$punishments"));

        pipeline.add(new Document("$match", new Document("punishments.issued", new Document("$type", "date"))));

        if (startDate != null) {
            // Post-filter to keep only punishment entries in-range after unwind.
            pipeline.add(new Document("$match", new Document("punishments.issued", new Document("$gte", startDate))));
        }

        Document byTypeFacet = new Document("$group", new Document("_id", "$punishments.typeOrdinal")
                .append("count", new Document("$sum", 1)));
        Document sortByCountDesc = new Document("$sort", new Document("count", -1));

        Document byStaffFacet = new Document("$group", new Document("_id", "$punishments.issuerName")
                .append("count", new Document("$sum", 1)));

        Document byDayFacet = new Document("$group", new Document("_id",
                new Document("$dateToString",
                        new Document("format", "%Y-%m-%d")
                                .append("date", "$punishments.issued")
                                .append("timezone", ANALYTICS_TIME_ZONE)))
                .append("count", new Document("$sum", 1)));
        Document sortByDayAsc = new Document("$sort", new Document("_id", 1));

        pipeline.add(new Document("$facet", new Document("byType", List.of(byTypeFacet, sortByCountDesc))
                .append("byStaff", List.of(byStaffFacet, sortByCountDesc))
                .append("daily", List.of(byDayFacet, sortByDayAsc))));

        return pipeline;
    }

    private Map<Integer, String> resolvePunishmentTypeNames(Server server) {
        Map<Integer, String> typeNames = new HashMap<>();
        punishmentTypeService.getPunishmentTypes(server).forEach(type ->
                typeNames.put(type.getOrdinal(), type.getName())
        );
        return typeNames;
    }

    private List<Document> toDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }

        List<Document> documents = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Document document) {
                documents.add(document);
            }
        }
        return documents;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String normalizeStaffName(Object rawStaffName) {
        if (rawStaffName == null) {
            return "Unknown";
        }

        String normalized = rawStaffName.toString().trim();
        return normalized.isBlank() ? "Unknown" : normalized;
    }

    private String formatPunishmentDay(String dateKey) {
        if (dateKey == null || dateKey.isBlank()) {
            return "Unknown";
        }

        try {
            return LocalDate.parse(dateKey).format(DateTimeFormatter.ofPattern("MMM dd"));
        } catch (Exception ignored) {
            return dateKey;
        }
    }

    private String normalizeCategory(String type) {
        if (type == null) return "Other";
        return switch (type.toLowerCase()) {
            case "bug" -> "Bug";
            case "support" -> "Support";
            case "appeal" -> "Appeal";
            case "player" -> "Player Report";
            case "chat" -> "Chat Report";
            case "staff" -> "Application";
            default -> "Other";
        };
    }

    private Date getStartDate(String period) {
        if ("all".equals(period)) {
            return null; // No date filter for all time
        }

        long now = System.currentTimeMillis();
        long daysMs = 24 * 60 * 60 * 1000L;

        return switch (period) {
            case "7d" -> new Date(now - 7 * daysMs);
            case "90d" -> new Date(now - 90 * daysMs);
            case "1y" -> new Date(now - 365 * daysMs);
            default -> new Date(now - 30 * daysMs);
        };
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
