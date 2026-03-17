package gg.modl.backend.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import gg.modl.backend.dashboard.dto.response.MinecraftDashboardStatsResponse;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TicketMongoRepository ticketRepository;

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private StaffMongoRepository staffRepository;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private Server server;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
            ticketRepository,
            playerRepository,
            staffRepository,
            punishmentTypeService,
            statusCalculator
        );
    }

    @Test
    void getMinecraftStatsAggregatesRepositoryResults() {
        List<String> staffUuids = List.of("uuid-1", "uuid-2");

        Player punishedPlayer = Player.builder()
            .minecraftUuid(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .punishments(List.of(
                punishment("active-ban", 2, "spam"),
                punishment("active-mute", 1, "toxicity")
            ))
            .build();
        Player inactivePlayer = Player.builder()
            .minecraftUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .punishments(List.of(punishment("inactive-ban", 2, "expired")))
            .build();

        when(ticketRepository.countUnresolvedReports(server)).thenReturn(3L);
        when(ticketRepository.countUnresolvedTickets(server)).thenReturn(4L);
        when(staffRepository.findAssignedMinecraftUuids(server)).thenReturn(staffUuids);
        when(playerRepository.countOnlineByUuids(eq(server), eq(staffUuids))).thenReturn(2L);
        when(playerRepository.countOnlinePlayers(server)).thenReturn(12L);
        when(playerRepository.countAll(server)).thenReturn(50L);
        when(playerRepository.findWithPunishmentsProjected(server)).thenReturn(List.of(punishedPlayer, inactivePlayer));
        when(punishmentTypeService.getPunishmentTypes(server)).thenReturn(List.of(
            punishmentType("Mute", 1, "Social"),
            punishmentType("Ban", 2, "Administrative")
        ));
        when(statusCalculator.isPunishmentActive(any(Punishment.class))).thenAnswer(invocation ->
            !((Punishment) invocation.getArgument(0)).getId().startsWith("inactive")
        );

        MinecraftDashboardStatsResponse response = dashboardService.getMinecraftStats(server);

        assertEquals(3L, response.unresolvedReports());
        assertEquals(4L, response.unresolvedTickets());
        assertEquals(2L, response.onlineStaff());
        assertEquals(12L, response.onlinePlayers());
        assertEquals(1L, response.activeBans());
        assertEquals(1L, response.activeMutes());
        assertEquals(2L, response.totalActivePunishments());
        assertEquals(50L, response.totalPlayers());
    }

    private static Punishment punishment(String id, int ordinal, String reason) {
        return new Punishment(
            id,
            ordinal,
            "Moderator",
            null,
            new Date(),
            null,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            Map.of("reason", reason)
        );
    }

    private static PunishmentType punishmentType(String name, int ordinal, String category) {
        PunishmentType punishmentType = new PunishmentType();
        punishmentType.setName(name);
        punishmentType.setOrdinal(ordinal);
        punishmentType.setCategory(category);
        return punishmentType;
    }
}
