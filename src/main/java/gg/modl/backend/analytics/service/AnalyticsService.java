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

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final DynamicMongoTemplateProvider mongoProvider;

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

        Aggregation statusAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("created").gte(startDate).and("status").ne("Unfinished")),
                Aggregation.group("status").count().as("count")
        );
        List<Document> statusResults = template.aggregate(statusAgg, CollectionName.TICKETS, Document.class).getMappedResults();
        List<TicketAnalyticsResponse.StatusCount> byStatus = statusResults.stream()
                .map(doc -> new TicketAnalyticsResponse.StatusCount(doc.getString("_id"), doc.getInteger("count", 0)))
                .toList();

        Aggregation categoryAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("created").gte(startDate).and("status").ne("Unfinished")),
                Aggregation.group("type").count().as("count")
        );
        List<Document> categoryResults = template.aggregate(categoryAgg, CollectionName.TICKETS, Document.class).getMappedResults();
        List<TicketAnalyticsResponse.CategoryCount> byCategory = categoryResults.stream()
                .map(doc -> new TicketAnalyticsResponse.CategoryCount(normalizeCategory(doc.getString("_id")), doc.getInteger("count", 0)))
                .toList();

        List<TicketAnalyticsResponse.CategoryResolutionTime> avgResolution = Collections.emptyList();
        List<TicketAnalyticsResponse.DailyTicket> dailyTickets = Collections.emptyList();

        return new TicketAnalyticsResponse(byStatus, byCategory, avgResolution, dailyTickets);
    }

    public PunishmentAnalyticsResponse getPunishmentAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        List<PunishmentAnalyticsResponse.TypeCount> byType = Collections.emptyList();
        List<PunishmentAnalyticsResponse.SeverityCount> bySeverity = Collections.emptyList();
        List<PunishmentAnalyticsResponse.DailyPunishment> dailyPunishments = Collections.emptyList();
        List<PunishmentAnalyticsResponse.StaffPunishment> byStaff = Collections.emptyList();

        return new PunishmentAnalyticsResponse(byType, bySeverity, dailyPunishments, byStaff);
    }

    public AuditLogsAnalyticsResponse getAuditLogsAnalytics(Server server, String period) {
        MongoTemplate template = getTemplate(server);
        Date startDate = getStartDate(period);

        // Aggregate logs by level
        Aggregation levelAgg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("created").gte(startDate)),
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

            // Count as new player if their first login is within the period
            if (earliestFirstLogin != null && earliestFirstLogin.after(startDate)) {
                String dateKey = java.time.Instant.ofEpochMilli(earliestFirstLogin.getTime())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .format(dateFormatter);
                dailyNewPlayers.merge(dateKey, 1, Integer::sum);
            }

            // Process each IP entry for country stats and suspicious activity
            for (var ipEntry : player.getIpAddresses()) {
                // Count logins by country (only for logins within period)
                if (ipEntry.getCountry() != null && !ipEntry.getCountry().isEmpty()) {
                    long loginsInPeriod = ipEntry.getLogins() != null
                            ? ipEntry.getLogins().stream().filter(d -> d.after(startDate)).count()
                            : 0;
                    if (loginsInPeriod > 0 || (ipEntry.getFirstLogin() != null && ipEntry.getFirstLogin().after(startDate))) {
                        countryLogins.merge(ipEntry.getCountry(), (int) Math.max(1, loginsInPeriod), Integer::sum);
                    }
                }

                // Check for logins within period for suspicious activity counting
                boolean hasRecentLogin = (ipEntry.getFirstLogin() != null && ipEntry.getFirstLogin().after(startDate))
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
