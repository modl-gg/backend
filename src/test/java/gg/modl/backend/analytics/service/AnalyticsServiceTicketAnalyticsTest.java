package gg.modl.backend.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.analytics.dto.response.TicketAnalyticsResponse;
import gg.modl.backend.database.mongo.repository.AnalyticsMongoRepository;
import gg.modl.backend.player.service.IssuerNameResolver;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.service.StaffService;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTicketAnalyticsTest {

    private AnalyticsMongoRepository analyticsRepository;
    private AnalyticsService service;
    private Server server;

    @BeforeEach
    void setUp() {
        analyticsRepository = mock(AnalyticsMongoRepository.class);
        service = new AnalyticsService(
            analyticsRepository,
            mock(PunishmentTypeService.class),
            mock(IssuerNameResolver.class),
            mock(StaffService.class)
        );
        server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
    }

    @Test
    void ticketAnalyticsComputesAverageResolutionHoursByCategory() {
        when(analyticsRepository.aggregateTicketStatusCounts(eq(server), any())).thenReturn(List.of());
        when(analyticsRepository.aggregateTicketCategoryCounts(eq(server), any())).thenReturn(List.of());
        when(analyticsRepository.aggregateDailyTicketCounts(eq(server), any(), any())).thenReturn(List.of());
        when(analyticsRepository.aggregateAvgResolutionByCategory(eq(server), any(Date.class)))
            .thenReturn(List.of(
                new AnalyticsMongoRepository.CategoryAvgResolution("bug", 7_200_000.0),
                new AnalyticsMongoRepository.CategoryAvgResolution("support", 1_800_000.0)));

        TicketAnalyticsResponse response = service.getTicketAnalytics(server, "30d");

        List<TicketAnalyticsResponse.CategoryResolutionTime> avgResolution = response.avgResolutionByCategory();
        assertEquals(2, avgResolution.size());
        assertEquals("Bug Report", avgResolution.get(0).category());
        assertEquals(2.0, avgResolution.get(0).avgHours(), 1e-9);
        assertEquals("General Support", avgResolution.get(1).category());
        assertEquals(0.5, avgResolution.get(1).avgHours(), 1e-9);
    }
}
