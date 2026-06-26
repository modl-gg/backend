package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.NoteEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerLookupServiceTest {
    private PlayerLookupService service;
    private PlayerMongoRepository playerRepository;
    private Server server;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerMongoRepository.class);
        service = new PlayerLookupService(
            playerRepository,
            mock(PunishmentTypeService.class),
            mock(MojangApiService.class),
            mock(PlayerStatusCalculator.class),
            mock(IssuerNameResolver.class),
            mock(StaffMongoRepository.class),
            mock(PlayerService.class)
        );
        server = new Server("Demo", "demo", "server_demo", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void getPlayerByUuidLowercasesUuidBeforeQueryingPlayerRepository() {
        when(playerRepository.findByMinecraftUuid(eq(server), eq("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")))
            .thenReturn(java.util.Optional.empty());

        service.getPlayerByUuid(server, "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", null, null);

        verify(playerRepository).findByMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void toPlayerProfileAppliesLimitsToEmbeddedCollectionsAndKeepsTotals() {
        Player player = Player.builder()
            .id("player-id")
            .minecraftUuid(UUID.randomUUID())
            .usernames(List.of(new UsernameEntry("Byteful", new Date(1_000L))))
            .notes(List.of(
                note("note-1", 1_000L),
                note("note-2", 2_000L),
                note("note-3", 3_000L),
                note("note-4", 4_000L)
            ))
            .punishments(List.of(
                punishment("punishment-1", 1_000L),
                punishment("punishment-2", 2_000L),
                punishment("punishment-3", 3_000L),
                punishment("punishment-4", 4_000L),
                punishment("punishment-5", 5_000L)
            ))
            .data(new HashMap<>())
            .build();

        Map<String, Object> profile = service.toPlayerProfile(server, player, List.<PunishmentType>of(), 3, 2);

        List<?> punishments = (List<?>) profile.get("punishments");
        List<?> notes = (List<?>) profile.get("notes");
        assertEquals(3, punishments.size());
        assertEquals(2, notes.size());
        assertEquals(5, profile.get("punishmentCount"));
        assertEquals(4, profile.get("noteCount"));
        // Most-recent N, newest first.
        assertEquals("punishment-5", ((Map<?, ?>) punishments.get(0)).get("id"));
        assertEquals("punishment-4", ((Map<?, ?>) punishments.get(1)).get("id"));
        assertEquals("punishment-3", ((Map<?, ?>) punishments.get(2)).get("id"));
        assertEquals("note-4", ((Map<?, ?>) notes.get(0)).get("id"));
        assertEquals("note-3", ((Map<?, ?>) notes.get(1)).get("id"));
    }

    private NoteEntry note(String id, long dateMillis) {
        return NoteEntry.builder()
            .id(id)
            .text(id + " text")
            .date(new Date(dateMillis))
            .issuerName("Mod")
            .build();
    }

    private Punishment punishment(String id, long issuedMillis) {
        return new Punishment(
            id,
            1,
            "Mod",
            null,
            new Date(issuedMillis),
            null,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new HashMap<>()
        );
    }
}
