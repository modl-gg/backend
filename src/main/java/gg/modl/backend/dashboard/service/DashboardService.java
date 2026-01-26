package gg.modl.backend.dashboard.service;

import gg.modl.backend.dashboard.dto.response.ActivityItemResponse;
import gg.modl.backend.dashboard.dto.response.DashboardMetricsResponse;
import gg.modl.backend.dashboard.dto.response.RecentPunishmentResponse;
import gg.modl.backend.dashboard.dto.response.RecentTicketResponse;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final DynamicMongoTemplateProvider mongoProvider;

    public DashboardMetricsResponse getMetrics(Server server) {
        MongoTemplate template = getTemplate(server);

        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        Date thirtyDaysAgo = new Date(now - thirtyDaysMs);
        Date sixtyDaysAgo = new Date(now - 2 * thirtyDaysMs);

        long totalTickets = template.count(new Query(), Ticket.class, CollectionName.TICKETS);
        long openTickets = template.count(Query.query(Criteria.where("status").is("Open")), Ticket.class, CollectionName.TICKETS);
        long totalPlayers = template.count(new Query(), Player.class, CollectionName.PLAYERS);
        long totalStaff = template.count(new Query(), Staff.class, CollectionName.STAFF);

        long totalPunishments = 0;
        long activePunishments = 0;

        long recentTickets = template.count(Query.query(Criteria.where("created").gte(thirtyDaysAgo)), Ticket.class, CollectionName.TICKETS);
        long prevTickets = template.count(Query.query(Criteria.where("created").gte(sixtyDaysAgo).lt(thirtyDaysAgo)), Ticket.class, CollectionName.TICKETS);
        int ticketsTrend = prevTickets > 0 ? (int) Math.round(((double) (recentTickets - prevTickets) / prevTickets) * 100) : 0;

        return new DashboardMetricsResponse(
                totalTickets,
                openTickets,
                totalPlayers,
                totalPunishments,
                activePunishments,
                totalStaff,
                ticketsTrend,
                0
        );
    }

    public List<RecentTicketResponse> getRecentTickets(Server server, int limit) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("status").ne("Unfinished"))
                .with(Sort.by(Sort.Direction.DESC, "created"))
                .limit(limit);

        List<Ticket> tickets = template.find(query, Ticket.class, CollectionName.TICKETS);

        return tickets.stream()
                .map(t -> {
                    String initialMessage = null;
                    if (t.getReplies() != null && !t.getReplies().isEmpty()) {
                        TicketReply firstReply = t.getReplies().get(0);
                        if (firstReply.getContent() != null) {
                            initialMessage = firstReply.getContent();
                        }
                    }

                    return new RecentTicketResponse(
                            t.getId(),
                            t.getSubject() != null ? t.getSubject() : "No Subject",
                            initialMessage,
                            t.getStatus() != null ? t.getStatus().toLowerCase() : "open",
                            t.getPriority() != null ? t.getPriority().toLowerCase() : "medium",
                            t.getCreated(),
                            t.getCreatorName() != null ? t.getCreatorName() : "Unknown",
                            t.getType() != null ? t.getType().toLowerCase() : "support"
                    );
                })
                .toList();
    }

    public List<RecentPunishmentResponse> getRecentPunishments(Server server, int limit) {
        return Collections.emptyList();
    }

    private static final int MAX_ACTIVITY_LIMIT = 100;
    private static final int MAX_DAYS = 90;
    private static final int MAX_QUERY_RESULTS = 200;

    public List<ActivityItemResponse> getRecentActivity(Server server, String staffEmail, int limit, int days) {
        MongoTemplate template = getTemplate(server);
        List<ActivityItemResponse> activities = new ArrayList<>();

        int safeLimit = Math.max(1, Math.min(limit, MAX_ACTIVITY_LIMIT));
        int safeDays = Math.max(1, Math.min(days, MAX_DAYS));

        String staffUsername = getStaffUsernameByEmail(template, staffEmail);
        if (staffUsername == null) {
            return activities;
        }

        Date cutoffDate = new Date(System.currentTimeMillis() - (long) safeDays * 24 * 60 * 60 * 1000);

        try {
            Query ticketQuery = Query.query(new Criteria().andOperator(
                    Criteria.where("created").gte(cutoffDate),
                    new Criteria().orOperator(
                            Criteria.where("creator").is(staffUsername),
                            Criteria.where("assignedTo").is(staffUsername),
                            Criteria.where("replies.name").is(staffUsername)
                    )
            )).limit(MAX_QUERY_RESULTS);
            List<Ticket> tickets = template.find(ticketQuery, Ticket.class, CollectionName.TICKETS);

            for (Ticket ticket : tickets) {
                if (ticket.getCreator() != null && ticket.getCreator().equals(staffUsername)
                        && ticket.getCreated() != null && ticket.getCreated().after(cutoffDate)) {
                    activities.add(new ActivityItemResponse(
                            "ticket-created-" + ticket.getId(),
                            "new_ticket",
                            "blue",
                            "Created ticket: " + (ticket.getSubject() != null ? ticket.getSubject() : "No Subject"),
                            ticket.getCreated(),
                            "Created " + (ticket.getCategory() != null ? ticket.getCategory() : "Support") + " ticket",
                            List.of(new ActivityItemResponse.ActivityAction("View Ticket", "/panel/tickets/" + ticket.getId(), true))
                    ));
                }

                if (ticket.getReplies() != null) {
                    for (TicketReply reply : ticket.getReplies()) {
                        if (reply.getCreated() != null && reply.getCreated().after(cutoffDate)) {
                            boolean isStaffReply = staffUsername.equals(reply.getName());
                            String actionType = isStaffReply ? "My reply" : "New reply";
                            String color = isStaffReply ? "green" : "blue";
                            String description = isStaffReply
                                    ? "You replied to " + (ticket.getCategory() != null ? ticket.getCategory() : "Support") + " ticket"
                                    : reply.getName() + " replied to " + (ticket.getCategory() != null ? ticket.getCategory() : "Support") + " ticket";

                            activities.add(new ActivityItemResponse(
                                    "ticket-reply-" + ticket.getId() + "-" + reply.getCreated().getTime(),
                                    "mod_action",
                                    color,
                                    actionType + " on ticket: " + (ticket.getSubject() != null ? ticket.getSubject() : "No Subject"),
                                    reply.getCreated(),
                                    description,
                                    List.of(new ActivityItemResponse.ActivityAction("View Ticket", "/panel/tickets/" + ticket.getId(), true))
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching ticket activities", e);
        }

        try {
            Query playerQuery = Query.query(
                    Criteria.where("punishments.issuerName").is(staffUsername)
                            .and("punishments.issued").gte(cutoffDate)
            ).limit(MAX_QUERY_RESULTS);
            List<Player> players = template.find(playerQuery, Player.class, CollectionName.PLAYERS);

            for (Player player : players) {
                String username = player.getUsernames() != null && !player.getUsernames().isEmpty()
                        ? player.getUsernames().get(player.getUsernames().size() - 1).username()
                        : "Unknown";

                if (player.getPunishments() != null) {
                    for (Punishment punishment : player.getPunishments()) {
                        if (staffUsername.equals(punishment.getIssuerName())
                                && punishment.getIssued() != null
                                && punishment.getIssued().after(cutoffDate)) {
                            activities.add(new ActivityItemResponse(
                                    "punishment-" + punishment.getId(),
                                    "new_punishment",
                                    "red",
                                    "Applied punishment to " + username,
                                    punishment.getIssued(),
                                    "Applied punishment (Type: " + punishment.getType_ordinal() + ")",
                                    List.of(new ActivityItemResponse.ActivityAction("View Player", "/panel/players/" + player.getMinecraftUuid(), true))
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching punishment activities", e);
        }

        activities.sort((a, b) -> b.time().compareTo(a.time()));

        if (activities.size() > safeLimit) {
            return activities.subList(0, safeLimit);
        }

        return activities;
    }

    private String getStaffUsernameByEmail(MongoTemplate template, String email) {
        Query query = Query.query(Criteria.where("email").is(email));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);
        return staff != null ? staff.getUsername() : null;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
