package gg.modl.backend.dashboard.service;

import gg.modl.backend.dashboard.dto.response.ActivityItemResponse;
import gg.modl.backend.dashboard.dto.response.DashboardMetricsResponse;
import gg.modl.backend.dashboard.dto.response.MinecraftDashboardStatsResponse;
import gg.modl.backend.dashboard.dto.response.RecentPunishmentResponse;
import gg.modl.backend.dashboard.dto.response.RecentTicketResponse;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.fields.TicketFields;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketPriority;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.util.TicketAssigneeUtil;
import gg.modl.backend.util.PlayerDataUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final TicketMongoRepository ticketRepository;
    private final PlayerMongoRepository playerRepository;
    private final StaffMongoRepository staffRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final PlayerStatusCalculator statusCalculator;
    private static final int RECENT_PUNISHMENT_WINDOW_DAYS = 7;
    private static final int MAX_RECENT_TICKETS_LIMIT = 20;
    private static final int MAX_RECENT_PUNISHMENTS_LIMIT = 20;
    private static final int MAX_ACTIVITY_LIMIT = 100;
    private static final int MAX_DAYS = 90;
    private static final int MAX_QUERY_RESULTS = 200;

    public MinecraftDashboardStatsResponse getMinecraftStats(Server server) {
        Query unresolvedReportsQuery = Query.query(new Criteria().andOperator(
            MongoQueries.where(TicketFields.TYPE).in(TicketCategory.reportCategoryIds()),
            MongoQueries.where(TicketFields.STATUS).in(TicketStatus.OPEN.getId(), TicketStatus.UNFINISHED.getId())
        ));
        long unresolvedReports = ticketRepository.count(server, unresolvedReportsQuery);

        Query unresolvedTicketsQuery = Query.query(new Criteria().andOperator(
            MongoQueries.where(TicketFields.TYPE).in(
                TicketCategory.SUPPORT.getId(),
                TicketCategory.BUG.getId(),
                TicketCategory.APPEAL.getId()
            ),
            MongoQueries.where(TicketFields.STATUS).in(TicketStatus.OPEN.getId(), TicketStatus.UNFINISHED.getId())
        ));
        long unresolvedTickets = ticketRepository.count(server, unresolvedTicketsQuery);

        long onlineStaff = countActiveStaff(server);
        long onlinePlayers = playerRepository.count(server, Query.query(MongoQueries.where(PlayerFields.DATA_IS_ONLINE).is(true)));
        long totalPlayers = playerRepository.count(server, new Query());

        ActivePunishmentCounts punishmentCounts = countActivePunishments(server);

        return new MinecraftDashboardStatsResponse(
            unresolvedReports,
            unresolvedTickets,
            onlineStaff,
            onlinePlayers,
            punishmentCounts.bans,
            punishmentCounts.mutes,
            punishmentCounts.total,
            totalPlayers
        );
    }

    private long countActiveStaff(Server server) {
        Query assignedStaffQuery = Query.query(MongoQueries.where(StaffFields.ASSIGNED_MINECRAFT_UUID).exists(true).ne(null).ne(""));
        MongoQueries.include(assignedStaffQuery, StaffFields.ASSIGNED_MINECRAFT_UUID);
        List<String> assignedUuids = staffRepository.find(server, assignedStaffQuery)
            .stream()
            .map(Staff::getAssignedMinecraftUuid)
            .filter(uuid -> uuid != null && !uuid.isBlank())
            .distinct()
            .toList();

        if (assignedUuids.isEmpty()) {
            return 0;
        }

        Query onlineStaffQuery = Query.query(new Criteria().andOperator(
            MongoQueries.where(PlayerFields.MINECRAFT_UUID).in(assignedUuids),
            MongoQueries.where(PlayerFields.DATA_IS_ONLINE).is(true)
        ));
        return playerRepository.count(server, onlineStaffQuery);
    }

    private ActivePunishmentCounts countActivePunishments(Server server) {
        Query punishmentsQuery = Query.query(MongoQueries.where(PlayerFields.PUNISHMENTS).exists(true));
        MongoQueries.include(punishmentsQuery, PlayerFields.PUNISHMENTS);

        Map<Integer, PunishmentType> punishmentTypesByOrdinal = buildPunishmentTypeByOrdinal(server);
        long activeBans = 0;
        long activeMutes = 0;
        long totalPunishments = 0;

        for (Player player : playerRepository.find(server, punishmentsQuery)) {
            if (player.getPunishments() == null || player.getPunishments().isEmpty()) {
                continue;
            }

            for (Punishment punishment : player.getPunishments()) {
                if (!isPunishmentActiveSafely(punishment)) {
                    continue;
                }

                totalPunishments++;
                PunishmentType punishmentType = punishmentTypesByOrdinal.get(punishment.getTypeOrdinal());
                if (punishmentType == null) {
                    continue;
                }

                if (punishmentType.isBan()) {
                    activeBans++;
                }
                if (punishmentType.isMute()) {
                    activeMutes++;
                }
            }
        }

        return new ActivePunishmentCounts(activeBans, activeMutes, totalPunishments);
    }

    private Map<Integer, PunishmentType> buildPunishmentTypeByOrdinal(Server server) {
        Map<Integer, PunishmentType> punishmentTypes = new LinkedHashMap<>();
        for (PunishmentType punishmentType : punishmentTypeService.getPunishmentTypes(server)) {
            punishmentTypes.put(punishmentType.getOrdinal(), punishmentType);
        }
        return punishmentTypes;
    }

    private boolean isPunishmentActiveSafely(Punishment punishment) {
        try {
            return statusCalculator.isPunishmentActive(punishment);
        } catch (Exception exception) {
            log.warn("Failed to calculate punishment active state for punishment id={}", punishment.getId(), exception);
            return false;
        }
    }

    public DashboardMetricsResponse getMetrics(Server server) {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        Date thirtyDaysAgo = new Date(now - thirtyDaysMs);
        Date sixtyDaysAgo = new Date(now - 2 * thirtyDaysMs);

        long totalTickets = ticketRepository.count(server, new Query());
        long openTickets = ticketRepository.count(server, Query.query(MongoQueries.where(TicketFields.STATUS).is(TicketStatus.OPEN.getId())));
        long totalPlayers = playerRepository.count(server, new Query());
        long totalStaff = staffRepository.count(server, new Query());

        long totalPunishments = 0;
        long activePunishments = 0;

        long recentTickets = ticketRepository.count(server, Query.query(MongoQueries.where(TicketFields.CREATED).gte(thirtyDaysAgo)));
        long prevTickets = ticketRepository.count(server, Query.query(MongoQueries.where(TicketFields.CREATED).gte(sixtyDaysAgo).lt(thirtyDaysAgo)));
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
        int safeLimit = clampLimit(limit, MAX_RECENT_TICKETS_LIMIT);

        Query query = Query.query(MongoQueries.where(TicketFields.STATUS).ne(TicketStatus.UNFINISHED.getId()))
            .with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.CREATED))
            .limit(safeLimit);

        MongoQueries.include(
            query,
            TicketFields.SUBJECT,
            TicketFields.STATUS,
            TicketFields.PRIORITY,
            TicketFields.CREATED,
            TicketFields.CREATOR_NAME,
            TicketFields.TYPE,
            TicketFields.REPLIES
        );
        query.fields().slice(TicketFields.REPLIES, 1);

        List<Ticket> tickets = ticketRepository.find(server, query);

        return tickets.stream()
            .map(ticket -> {
                String initialMessage = null;
                if (ticket.getReplies() != null && !ticket.getReplies().isEmpty()) {
                    TicketReply firstReply = ticket.getReplies().get(0);
                    if (firstReply.getContent() != null) {
                        initialMessage = firstReply.getContent();
                    }
                }

                return new RecentTicketResponse(
                    ticket.getId(),
                    ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                    initialMessage,
                    ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
                    ticket.getPriority() != null ? ticket.getPriority().getId() : TicketPriority.NORMAL.getId(),
                    ticket.getCreated(),
                    ticket.getCreatorName() != null ? ticket.getCreatorName() : "Unknown",
                    ticket.getType() != null ? ticket.getType().getId() : TicketCategory.SUPPORT.getId()
                );
            })
            .toList();
    }

    private int clampLimit(int value, int max) {
        return Math.max(1, Math.min(value, max));
    }

    public List<RecentPunishmentResponse> getRecentPunishments(Server server, int limit) {
        int safeLimit = clampLimit(limit, MAX_RECENT_PUNISHMENTS_LIMIT);
        Date cutoff = new Date(System.currentTimeMillis() - (RECENT_PUNISHMENT_WINDOW_DAYS * 24L * 60 * 60 * 1000));

        Map<Integer, String> punishmentTypeNameByOrdinal = buildPunishmentTypeNameByOrdinal(server);
        List<Document> punishmentRows = fetchRecentPunishmentRows(
            server,
            MongoQueries.where(PlayerFields.PUNISHMENT_ISSUED).gte(cutoff),
            safeLimit
        );

        List<RecentPunishmentResponse> results = new ArrayList<>();
        for (Document row : punishmentRows) {
            Punishment punishment = readPunishment(server, row.get("punishment", Document.class));
            if (punishment == null || punishment.getIssued() == null || punishment.getIssued().before(cutoff)) {
                continue;
            }

            normalizePunishmentCollections(punishment);

            String reason = "";
            if (punishment.getData() != null && punishment.getData().get("reason") != null) {
                reason = String.valueOf(punishment.getData().get("reason"));
            }

            String typeName = punishmentTypeNameByOrdinal.getOrDefault(punishment.getTypeOrdinal(), "Unknown");
            String playerName = PlayerDataUtils.extractLatestUsername(row.get(PlayerFields.USERNAMES));
            String playerUuid = PlayerDataUtils.extractMinecraftUuid(row);

            results.add(new RecentPunishmentResponse(
                punishment.getId(),
                playerName,
                playerUuid,
                typeName,
                reason,
                punishment.getIssuerName() != null ? punishment.getIssuerName() : "Unknown",
                punishment.getIssued(),
                isPunishmentActiveSafely(punishment)
            ));
        }

        if (results.size() > safeLimit) {
            return results.subList(0, safeLimit);
        }

        return results;
    }

    private List<Document> fetchRecentPunishmentRows(Server server, Criteria punishmentCriteria, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(punishmentCriteria),
            Aggregation.unwind(PlayerFields.PUNISHMENTS),
            Aggregation.match(punishmentCriteria),
            Aggregation.sort(MongoQueries.sort(Sort.Direction.DESC, PlayerFields.PUNISHMENT_ISSUED)),
            Aggregation.limit(limit),
            Aggregation.project(PlayerFields.MINECRAFT_UUID, PlayerFields.USERNAMES)
                .and(PlayerFields.PUNISHMENTS).as("punishment")
        );

        return playerRepository.aggregate(server, aggregation, Document.class).getMappedResults();
    }

    private Punishment readPunishment(Server server, Document punishmentDocument) {
        if (punishmentDocument == null) {
            return null;
        }

        try {
            return playerRepository.readPunishment(server, punishmentDocument);
        } catch (Exception exception) {
            log.warn("Failed to parse punishment document for dashboard response", exception);
            return null;
        }
    }

    private Map<Integer, String> buildPunishmentTypeNameByOrdinal(Server server) {
        Map<Integer, String> names = new HashMap<>();
        buildPunishmentTypeByOrdinal(server).forEach((ordinal, type) ->
            names.put(ordinal, type.getName()));
        return names;
    }

    private void normalizePunishmentCollections(Punishment punishment) {
        if (punishment.getModifications() == null) {
            punishment.setModifications(new ArrayList<>());
        }
        if (punishment.getNotes() == null) {
            punishment.setNotes(new ArrayList<>());
        }
        if (punishment.getEvidence() == null) {
            punishment.setEvidence(new ArrayList<>());
        }
        if (punishment.getAttachedTicketIds() == null) {
            punishment.setAttachedTicketIds(new ArrayList<>());
        }
    }

    public List<ActivityItemResponse> getRecentActivity(Server server, String staffEmail, int limit, int days) {
        List<ActivityItemResponse> activities = new ArrayList<>();

        int safeLimit = clampLimit(limit, MAX_ACTIVITY_LIMIT);
        int safeDays = clampLimit(days, MAX_DAYS);

        String staffUsername = getStaffUsernameByEmail(server, staffEmail);
        if (staffUsername == null) {
            return activities;
        }

        Date cutoffDate = new Date(System.currentTimeMillis() - (long) safeDays * 24 * 60 * 60 * 1000);

        fetchTicketActivities(server, staffUsername, cutoffDate, activities);
        fetchPunishmentActivities(server, staffUsername, cutoffDate, activities);

        activities.sort((left, right) -> right.time().compareTo(left.time()));

        if (activities.size() > safeLimit) {
            return activities.subList(0, safeLimit);
        }

        return activities;
    }

    private void fetchTicketActivities(Server server, String staffUsername, Date cutoffDate, List<ActivityItemResponse> activities) {
        try {
            String normalizedStaffUsername = TicketAssigneeUtil.normalizeSingle(staffUsername);

            List<Criteria> staffMatchCriteria = new ArrayList<>();
            staffMatchCriteria.add(MongoQueries.where(TicketFields.CREATOR_NAME).is(staffUsername));
            if (normalizedStaffUsername != null) {
                staffMatchCriteria.add(MongoQueries.where(TicketFields.ASSIGNED_TO).is(normalizedStaffUsername));
            }
            staffMatchCriteria.add(MongoQueries.where(TicketFields.REPLY_NAME).is(staffUsername));

            Query ticketQuery = Query.query(new Criteria().andOperator(
                MongoQueries.where(TicketFields.UPDATED_AT).gte(cutoffDate),
                new Criteria().orOperator(staffMatchCriteria.toArray(new Criteria[0]))
            )).with(MongoQueries.sort(Sort.Direction.DESC, TicketFields.UPDATED_AT)).limit(MAX_QUERY_RESULTS);

            MongoQueries.include(
                ticketQuery,
                TicketFields.SUBJECT,
                TicketFields.TYPE,
                TicketFields.CREATED,
                TicketFields.CREATOR_NAME,
                TicketFields.REPLY_NAME,
                TicketFields.REPLY_CREATED
            );

            List<Ticket> tickets = ticketRepository.find(server, ticketQuery);

            for (Ticket ticket : tickets) {
                if (ticket.getCreatorName() != null
                    && ticket.getCreatorName().equals(staffUsername)
                    && ticket.getCreated() != null
                    && ticket.getCreated().after(cutoffDate)) {
                    activities.add(new ActivityItemResponse(
                        "ticket-created-" + ticket.getId(),
                        "new_ticket",
                        "blue",
                        "Created ticket: " + (ticket.getSubject() != null ? ticket.getSubject() : "No Subject"),
                        ticket.getCreated(),
                        "Created " + displayCategory(ticket) + " ticket",
                        List.of(new ActivityItemResponse.ActivityAction("View Ticket", "/panel/tickets/" + ticket.getId(), true))
                    ));
                }

                if (ticket.getReplies() != null) {
                    for (TicketReply reply : ticket.getReplies()) {
                        if (reply.getCreated() == null || !reply.getCreated().after(cutoffDate)) {
                            continue;
                        }

                        boolean isStaffReply = staffUsername.equalsIgnoreCase(reply.getName());
                        String actionType = isStaffReply ? "My reply" : "New reply";
                        String color = isStaffReply ? "green" : "blue";
                        String replyName = reply.getName() != null ? reply.getName() : "Unknown";
                        String description = isStaffReply
                                             ? "You replied to " + displayCategory(ticket) + " ticket"
                                             : replyName + " replied to " + displayCategory(ticket) + " ticket";

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
        } catch (Exception exception) {
            log.error("Error fetching ticket activities", exception);
        }
    }

    private String displayCategory(Ticket ticket) {
        return ticket.getType() != null ? ticket.getType().getDisplayName() : TicketCategory.SUPPORT.getDisplayName();
    }

    private void fetchPunishmentActivities(Server server, String staffUsername, Date cutoffDate, List<ActivityItemResponse> activities) {
        try {
            Map<Integer, String> punishmentTypeNameByOrdinal = buildPunishmentTypeNameByOrdinal(server);
            Criteria punishmentCriteria = MongoQueries.where(PlayerFields.PUNISHMENT_ISSUER_NAME).is(staffUsername)
                .and(PlayerFields.PUNISHMENT_ISSUED).gte(cutoffDate);
            List<Document> punishmentRows = fetchRecentPunishmentRows(server, punishmentCriteria, MAX_QUERY_RESULTS);

            for (Document row : punishmentRows) {
                Punishment punishment = readPunishment(server, row.get("punishment", Document.class));
                if (punishment == null || punishment.getIssued() == null || punishment.getIssued().before(cutoffDate)) {
                    continue;
                }

                normalizePunishmentCollections(punishment);

                String username = PlayerDataUtils.extractLatestUsername(row.get(PlayerFields.USERNAMES));
                String punishmentTypeName = punishmentTypeNameByOrdinal.getOrDefault(punishment.getTypeOrdinal(), "Unknown");
                String playerUuid = PlayerDataUtils.extractMinecraftUuid(row);

                activities.add(new ActivityItemResponse(
                    "punishment-" + punishment.getId(),
                    "new_punishment",
                    "red",
                    "Applied " + punishmentTypeName + " to " + username,
                    punishment.getIssued(),
                    "Applied " + punishmentTypeName + " punishment",
                    List.of(new ActivityItemResponse.ActivityAction("View Player", "/panel/players/" + playerUuid, true))
                ));
            }
        } catch (Exception exception) {
            log.error("Error fetching punishment activities", exception);
        }
    }

    private String getStaffUsernameByEmail(Server server, String email) {
        Query query = Query.query(MongoQueries.where(StaffFields.EMAIL).is(email));
        MongoQueries.include(query, StaffFields.USERNAME);
        return staffRepository.findOne(server, query)
            .map(Staff::getUsername)
            .filter(username -> username != null && !username.isBlank())
            .orElse(null);
    }

    private record ActivePunishmentCounts(long bans, long mutes, long total) {}
}
