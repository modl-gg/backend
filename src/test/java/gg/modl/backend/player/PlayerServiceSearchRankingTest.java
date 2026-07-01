package gg.modl.backend.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.dto.response.PlayerSearchResult;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerServiceSearchRankingTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private RealtimeEventPublisher realtimePublisher;

    @Mock
    private PunishmentQueryService punishmentQueryService;

    @Mock
    private Server server;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository, statusCalculator, punishmentTypeService, realtimePublisher, punishmentQueryService);
    }

    @Test
    void exactCurrentUsernamePreferredOverPrefixedPartialMatch() {
        UUID javaUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bedrockUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        Player javaPlayer = player(javaUuid, List.of("UrgedRook8642"), null);
        Player bedrockPlayer = player(bedrockUuid, List.of(".UrgedRook8642"), null);

        when(playerRepository.searchByUsernamePattern(server, "UrgedRook8642", 100))
            .thenReturn(List.of(bedrockPlayer, javaPlayer));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, "UrgedRook8642");

        assertEquals(2, results.size());
        assertEquals(javaUuid.toString(), results.get(0).uuid());
        assertEquals("UrgedRook8642", results.get(0).username());
    }

    private static Player player(UUID uuid, List<String> usernames, Date lastLogin) {
        Player player = Player.builder()
            .id(uuid.toString())
            .minecraftUuid(uuid)
            .usernames(new ArrayList<>())
            .notes(new ArrayList<>())
            .ipAddresses(new ArrayList<>())
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        for (String username : usernames) {
            player.getUsernames().add(new UsernameEntry(username, new Date()));
        }
        if (lastLogin != null) {
            player.getData().put("lastLogin", lastLogin);
        }

        return player;
    }

    @Test
    void historicalExactMatchPreferredOverCurrentPrefixMatch() {
        UUID renamedUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID prefixedUuid = UUID.fromString("44444444-4444-4444-4444-444444444444");

        Player renamedPlayer = player(renamedUuid, List.of("Name123", "RenamedPlayer"), null);
        Player prefixedPlayer = player(prefixedUuid, List.of(".Name123"), null);

        when(playerRepository.searchByUsernamePattern(server, "Name123", 100))
            .thenReturn(List.of(prefixedPlayer, renamedPlayer));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, "Name123");

        assertEquals(2, results.size());
        assertEquals(renamedUuid.toString(), results.get(0).uuid());
        assertEquals("RenamedPlayer", results.get(0).username());
    }

    @Test
    void caseInsensitiveExactMatchPreferred() {
        UUID exactUuid = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID partialUuid = UUID.fromString("66666666-6666-6666-6666-666666666666");

        Player exactPlayer = player(exactUuid, List.of("NAME123"), null);
        Player partialPlayer = player(partialUuid, List.of(".name123"), null);

        when(playerRepository.searchByUsernamePattern(server, "name123", 100))
            .thenReturn(List.of(partialPlayer, exactPlayer));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, "name123");

        assertEquals(2, results.size());
        assertEquals(exactUuid.toString(), results.get(0).uuid());
        assertEquals("NAME123", results.get(0).username());
    }

    @Test
    void findBestByUsernamePrefersOnlineCurrentMatchOverOfflineDuplicate() {
        UUID offlineUuid = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID onlineUuid = UUID.fromString("99999999-9999-9999-9999-999999999999");

        Player offlinePlayer = player(offlineUuid, List.of("modltarget"), Date.from(Instant.parse("2026-04-20T10:15:30Z")));
        Player onlinePlayer = player(onlineUuid, List.of("modltarget"), Date.from(Instant.parse("2026-04-21T10:15:30Z")));
        onlinePlayer.getData().put("isOnline", true);

        when(playerRepository.searchByUsernamePattern(server, "modltarget", 100))
            .thenReturn(List.of(offlinePlayer, onlinePlayer));

        Player resolved = playerService.findBestByUsername(server, "modltarget").orElseThrow();

        assertSame(onlinePlayer, resolved);
    }

    @Test
    void findBestByUsernamePrefersCurrentExactMatchOverHistoricalExactMatch() {
        UUID renamedUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID currentUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Player renamedPlayer = player(renamedUuid, List.of("modltarget", "renamedtarget"), Date.from(Instant.parse("2026-04-21T09:15:30Z")));
        Player currentPlayer = player(currentUuid, List.of("modltarget"), Date.from(Instant.parse("2026-04-20T09:15:30Z")));

        when(playerRepository.searchByUsernamePattern(server, "modltarget", 100))
            .thenReturn(List.of(renamedPlayer, currentPlayer));

        Player resolved = playerService.findBestByUsername(server, "modltarget").orElseThrow();

        assertSame(currentPlayer, resolved);
    }

    @Test
    void uuidSearchNormalizesCaseBeforeQuerying() {
        UUID playerUuid = UUID.fromString("77777777-7777-7777-7777-777777777777");
        String uppercaseUuid = playerUuid.toString().toUpperCase(Locale.ROOT);

        Player player = player(playerUuid, List.of("SomePlayer"), Date.from(Instant.now()));
        when(playerRepository.findByMinecraftUuid(server, playerUuid.toString()))
            .thenReturn(java.util.Optional.of(player));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, uppercaseUuid);

        verify(playerRepository).findByMinecraftUuid(eq(server), eq(playerUuid.toString()));

        assertFalse(results.isEmpty());
        assertEquals(playerUuid.toString(), results.get(0).uuid());
    }
}
