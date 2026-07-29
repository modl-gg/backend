package gg.modl.backend.audit.service;

import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.database.mongo.repository.AuditLogRepository;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository.IdCountResult;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository.OrdinalCountResult;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository.StaffActivityResult;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository.StaffTicketResponseTime;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.util.DateRangeUtil;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.StaffService;
import java.util.ArrayList;
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
public class StaffPerformanceService {

    private final StaffActivityAnalyticsRepository staffActivityAnalyticsRepository;
    private final AuditLogRepository auditLogRepository;
    private final StaffMongoRepository staffMongoRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffService staffService;
    private final PermissionService permissionService;

    public List<StaffPerformanceResponse> getStaffPerformance(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        List<Staff> allStaff = staffMongoRepository.findAllStaff(server);
        Map<String, String> roleNamesById = permissionService.resolveRoleNames(server,
            allStaff.stream().map(Staff::getRoleId).toList());
        Map<String, StaffActivityResult> activityByUsername = indexStaffActivity(
            staffActivityAnalyticsRepository.aggregateLogActivityBySource(server, startDate));
        Map<String, Integer> ticketResponsesByStaff = indexIdCounts(
            staffActivityAnalyticsRepository.aggregateTicketResponseCounts(server, startDate));
        Map<String, Integer> punishmentsByStaff = countPunishmentsByStaff(server, startDate);
        Map<String, List<StaffTicketResponseTime>> ticketResponseTimesByStaff = indexTicketResponseTimes(
            staffActivityAnalyticsRepository.aggregateTicketResponseTimesByStaff(server, startDate));

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

            String roleName = staff.getRoleId() != null && !staff.getRoleId().isBlank()
                              ? roleNamesById.getOrDefault(staff.getRoleId(), staff.getRoleId())
                              : "User";
            int avgResponseTime = averageOf(
                ticketResponseTimesByStaff.getOrDefault(lowerUsername, List.of()).stream()
                    .map(source -> calculateResponseTimeMinutes(source.ticketCreated(), source.firstReply()))
                    .toList());
            performanceList.add(new StaffPerformanceResponse(
                staff.getId(),
                username,
                roleName,
                totalActions,
                ticketActions,
                moderationActions,
                avgResponseTime,
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

    private Map<String, List<StaffTicketResponseTime>> indexTicketResponseTimes(
        List<StaffTicketResponseTime> results) {
        Map<String, List<StaffTicketResponseTime>> map = new HashMap<>();
        for (StaffTicketResponseTime result : results) {
            if (result.staff() != null) {
                map.computeIfAbsent(result.staff().toLowerCase(), key -> new ArrayList<>()).add(result);
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
            staffActivityAnalyticsRepository.aggregatePunishmentCountsByIssuer(server, startDate);

        Set<String> issuerIdsToResolve = new HashSet<>();
        for (IdCountResult result : results) {
            if (result.id() != null && ObjectId.isValid(result.id())) {
                issuerIdsToResolve.add(result.id());
            }
        }
        Map<String, String> resolvedIds =
            staffMongoRepository.findUsernamesByIds(server, issuerIdsToResolve);

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

        long evidenceUploads = auditLogRepository.countEvidenceUploads(server, username, startDate);
        int avgResponseTime = averageResponseTimeMinutes(tickets);

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
            staffActivityAnalyticsRepository.aggregatePunishmentDetails(server, usernames, staffId, startDate);

        for (Document doc : results) {
            int typeOrdinal = doc.getInteger(AuditProjectionKeys.TYPE_ORDINAL, 0);
            String reason = doc.getString(AuditProjectionKeys.REASON);
            Object durationObj = doc.get(AuditProjectionKeys.DURATION);

            details.add(new StaffDetailsResponse.PunishmentDetail(
                doc.getString(AuditProjectionKeys.PUNISHMENT_ID),
                doc.getString(AuditProjectionKeys.PLAYER_ID),
                AuditDocumentUtil.extractPlayerNameFromDoc(doc),
                punishmentTypeService.getPunishmentTypeName(server, typeOrdinal),
                reason != null ? reason : "No reason provided",
                durationObj != null ? durationObj.toString() : null,
                doc.getDate("issued"),
                !AuditDocumentUtil.hasModificationType(doc, PunishmentModificationType.REMOVE.name(), PunishmentModificationType.REVOKE.name()),
                AuditDocumentUtil.hasModificationType(doc, PunishmentModificationType.ROLLBACK.name())
            ));
        }
        return details;
    }

    private List<StaffDetailsResponse.TicketDetail> getTicketDetails(
        Server server, String username, Date startDate) {
        List<StaffDetailsResponse.TicketDetail> details = new ArrayList<>();
        List<Document> results =
            staffActivityAnalyticsRepository.aggregateTicketDetails(server, username, startDate);

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

    private static int averageResponseTimeMinutes(List<StaffDetailsResponse.TicketDetail> tickets) {
        return averageOf(tickets.stream()
            .map(StaffDetailsResponse.TicketDetail::responseTime)
            .toList());
    }

    private static int averageOf(List<Integer> responseTimes) {
        if (responseTimes.isEmpty()) {
            return 0;
        }
        return (int) responseTimes.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
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
            staffActivityAnalyticsRepository.aggregateDailyPunishmentCounts(
                server, usernames, staffId, startDate);
        for (IdCountResult result : punishmentResults) {
            activityByDate.put(result.id(),
                new StaffDetailsResponse.DailyActivity(result.id(), result.count(), 0, 0));
        }

        List<IdCountResult> ticketResults =
            staffActivityAnalyticsRepository.aggregateDailyTicketResponseCounts(
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
            staffActivityAnalyticsRepository.aggregatePunishmentTypeBreakdown(
                server, usernames, staffId, startDate);

        for (OrdinalCountResult result : results) {
            String typeName = punishmentTypeService.getPunishmentTypeName(
                server, result.id() != null ? result.id() : 0);
            breakdown.add(new StaffDetailsResponse.PunishmentTypeBreakdown(typeName, result.count()));
        }
        return breakdown;
    }
}
