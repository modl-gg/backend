package gg.modl.backend.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.analytics.dto.response.OverviewResponse;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository;
import gg.modl.backend.player.service.IssuerNameResolver;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.service.StaffService;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsServiceOverviewTest {

    private AnalyticsMongoRepository analyticsRepository;
    private StaffService staffService;
    private AnalyticsService service;
    private Server server;

    @BeforeEach
    void setUp() {
        analyticsRepository = mock(AnalyticsMongoRepository.class);
        staffService = mock(StaffService.class);
        service = new AnalyticsService(
            analyticsRepository,
            mock(PunishmentTypeService.class),
            mock(IssuerNameResolver.class),
            staffService
        );
        server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        server.setCreatedAt(new Date(1_700_000_000_000L));
    }

    @Test
    void overviewStaffCountIncludesSuperAdmin() {
        when(analyticsRepository.loadOverviewStats(eq(server), any(Date.class), any(Date.class)))
            .thenReturn(new AnalyticsMongoRepository.OverviewStats(5, 10, 2, 3, 1, 0, 0));
        when(staffService.countStaffIncludingSuperAdmin(server)).thenReturn(1L);

        OverviewResponse overview = service.getOverview(server);

        assertEquals(1L, overview.totalStaff());
        assertEquals(5L, overview.totalTickets());
        assertEquals(10L, overview.totalPlayers());
        assertEquals(2L, overview.activeTickets());
    }

    @Test
    void overviewComputesPlayerChangeFromRecentVsPreviousPlayers() {
        when(analyticsRepository.loadOverviewStats(eq(server), any(Date.class), any(Date.class)))
            .thenReturn(new AnalyticsMongoRepository.OverviewStats(5, 10, 2, 8, 4, 15, 12));
        when(staffService.countStaffIncludingSuperAdmin(server)).thenReturn(1L);

        OverviewResponse overview = service.getOverview(server);

        assertEquals(100, overview.ticketChange());
        assertEquals(25, overview.playerChange());
    }

    @Test
    void overviewPlayerChangeIsZeroWhenNoPreviousPlayers() {
        when(analyticsRepository.loadOverviewStats(eq(server), any(Date.class), any(Date.class)))
            .thenReturn(new AnalyticsMongoRepository.OverviewStats(5, 10, 2, 8, 0, 15, 0));
        when(staffService.countStaffIncludingSuperAdmin(server)).thenReturn(1L);

        OverviewResponse overview = service.getOverview(server);

        assertEquals(0, overview.playerChange());
    }
}
