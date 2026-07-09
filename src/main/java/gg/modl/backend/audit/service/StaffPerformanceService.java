package gg.modl.backend.audit.service;

import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.IdCountResult;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.OrdinalCountResult;
import gg.modl.backend.database.mongo.repository.AuditMongoRepository.StaffActivityResult;
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

    private final AuditMongoRepository auditRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffService staffService;
    private final PermissionService permissionService;

    public List<StaffPerformanceResponse> getStaffPerformance(Server server, String period) {
        Date startDate = DateRangeUtil.getStartDate(period);

        List<Staff> allStaff = auditRepository.findAllStaff(server);
        Map<String, String> roleNamesById = permissionService.resolveRoleNames(server,
            allStaff.stream().map(Staff::getRoleId).toList());
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

            String roleName = staff.getRoleId() != null && !staff.getRoleId().isBlank()
                              ? roleNamesById.getOrDefault(staff.getRoleId(), staff.getRoleId())
                              : "User";
            performanceList.add(new StaffPerformanceResponse(
                staff.getId(),
                username,
                roleName,
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
}
