package gg.modl.backend.analytics.service;

import gg.modl.backend.analytics.dto.response.OverviewResponse;
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
