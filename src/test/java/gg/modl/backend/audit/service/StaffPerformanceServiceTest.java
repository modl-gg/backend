package gg.modl.backend.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import gg.modl.backend.audit.dto.response.StaffDetailsResponse;
import gg.modl.backend.audit.dto.response.StaffPerformanceResponse;
import gg.modl.backend.database.mongo.repository.AuditLogRepository;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository;
import gg.modl.backend.database.mongo.repository.StaffActivityAnalyticsRepository.StaffTicketResponseTime;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffPerformanceServiceTest {

    private static final String USERNAME = "Byteful";
    private static final String PERIOD = "all";
    private static final long MINUTE_MS = 60_000L;
    private static final Date TICKET_CREATED = new Date(1_000_000_000_000L);
    private static final Date FIRST_REPLY_A = new Date(TICKET_CREATED.getTime() + 5 * MINUTE_MS + 30_000L);
    private static final Date FIRST_REPLY_B = new Date(TICKET_CREATED.getTime() + 10 * MINUTE_MS);
    private static final Date FIRST_REPLY_C = new Date(TICKET_CREATED.getTime() + 2 * MINUTE_MS);
    private static final int EXPECTED_AVG = 5;

    @Mock
    private StaffActivityAnalyticsRepository staffActivityAnalyticsRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private StaffMongoRepository staffMongoRepository;
    @Mock
    private PunishmentTypeService punishmentTypeService;
    @Mock
    private StaffService staffService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private Server server;

    @Test
    void staffPerformanceAndStaffDetailsShareAvgResponseTime() {
        StaffPerformanceService service = new StaffPerformanceService(
            staffActivityAnalyticsRepository,
            auditLogRepository,
            staffMongoRepository,
            punishmentTypeService,
            staffService,
            permissionService);

        when(staffMongoRepository.findAllStaff(any())).thenReturn(List.of(
            Staff.builder().username(USERNAME).build()));
        when(permissionService.resolveRoleNames(any(), any())).thenReturn(Map.of());
        when(staffActivityAnalyticsRepository.aggregateLogActivityBySource(any(), any()))
            .thenReturn(List.of());
        when(staffActivityAnalyticsRepository.aggregateTicketResponseCounts(any(), any()))
            .thenReturn(List.of());
        when(staffActivityAnalyticsRepository.aggregatePunishmentCountsByIssuer(any(), any()))
            .thenReturn(List.of());
        when(staffMongoRepository.findUsernamesByIds(any(), any())).thenReturn(Map.of());
        when(staffActivityAnalyticsRepository.aggregateTicketResponseTimesByStaff(any(), any()))
            .thenReturn(List.of(
                new StaffTicketResponseTime("byteful", TICKET_CREATED, FIRST_REPLY_A),
                new StaffTicketResponseTime("byteful", TICKET_CREATED, FIRST_REPLY_B),
                new StaffTicketResponseTime("byteful", TICKET_CREATED, FIRST_REPLY_C)));

        when(staffService.getStaffByUsername(any(), any())).thenReturn(Optional.empty());
        when(staffActivityAnalyticsRepository.aggregatePunishmentDetails(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(staffActivityAnalyticsRepository.aggregateTicketDetails(any(), any(), any()))
            .thenReturn(List.of(
                ticketDetailDocument("t1", FIRST_REPLY_A),
                ticketDetailDocument("t2", FIRST_REPLY_B),
                ticketDetailDocument("t3", FIRST_REPLY_C)));
        when(staffActivityAnalyticsRepository.aggregateDailyPunishmentCounts(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(staffActivityAnalyticsRepository.aggregateDailyTicketResponseCounts(any(), any(), any()))
            .thenReturn(List.of());
        when(staffActivityAnalyticsRepository.aggregatePunishmentTypeBreakdown(any(), any(), any(), any()))
            .thenReturn(List.of());
        when(auditLogRepository.countEvidenceUploads(any(), any(), any())).thenReturn(0L);

        List<StaffPerformanceResponse> performance = service.getStaffPerformance(server, PERIOD);
        StaffDetailsResponse details = service.getStaffDetails(server, USERNAME, PERIOD);

        assertEquals(EXPECTED_AVG, performance.get(0).avgResponseTime());
        assertEquals(details.summary().avgResponseTime(), performance.get(0).avgResponseTime());
    }

    private static Document ticketDetailDocument(String id, Date firstReply) {
        return new Document("_id", id)
            .append("ticketCreated", TICKET_CREATED)
            .append("firstReply", firstReply);
    }
}
