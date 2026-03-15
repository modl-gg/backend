package gg.modl.backend.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerServicePersistenceTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private Server server;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository, statusCalculator, punishmentTypeService);
    }

    @Test
    void loginPlayerMutatesAggregateAndPersistsThroughRepository() {
        UUID playerUuid = UUID.randomUUID();
        Player player = Player.builder()
            .id("player-1")
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("OldName", new Date(1_000L)))))
            .ipAddresses(new ArrayList<>())
            .notes(new ArrayList<>())
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        when(playerRepository.findByMinecraftUuid(server, playerUuid)).thenReturn(Optional.of(player));

        Player updated = playerService.loginPlayer(
            server,
            playerUuid,
            "NewName",
            "127.0.0.1",
            Map.of("country", "US", "region", "Virginia", "asn", "AS123", "proxy", true, "hosting", false),
            "skin-hash",
            "hub"
        );

        assertNotNull(updated);
        verify(playerRepository).updateLoginState(eq(server), eq(updated));
        Player savedPlayer = updated;
        assertEquals(2, savedPlayer.getUsernames().size());
        assertEquals("NewName", savedPlayer.getUsernames().get(1).username());
        assertEquals(1, savedPlayer.getIpAddresses().size());
        IPEntry ipEntry = savedPlayer.getIpAddresses().get(0);
        assertEquals("127.0.0.1", ipEntry.getIpAddress());
        assertEquals("US", ipEntry.getCountry());
        assertEquals("Virginia", ipEntry.getRegion());
        assertEquals("AS123", ipEntry.getAsn());
        assertTrue(ipEntry.isProxy());
        assertTrue(Boolean.TRUE.equals(savedPlayer.getData().get("isOnline")));
        assertEquals("skin-hash", savedPlayer.getData().get("lastSkinHash"));
        assertEquals("hub", savedPlayer.getData().get("lastServer"));
        assertNotNull(savedPlayer.getData().get("firstJoin"));
        assertNotNull(savedPlayer.getData().get("lastLogin"));
    }

    @Test
    void updateIpGeoDataMutatesExistingIpEntryAndPersistsThroughRepository() {
        Player player = Player.builder()
            .id("player-1")
            .minecraftUuid(UUID.randomUUID())
            .usernames(new ArrayList<>())
            .ipAddresses(new ArrayList<>(List.of(IPEntry.builder()
                .ipAddress("127.0.0.1")
                .firstLogin(new Date())
                .logins(new ArrayList<>())
                .build())))
            .notes(new ArrayList<>())
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        when(playerRepository.findByMinecraftUuid(server, player.getMinecraftUuid().toString())).thenReturn(Optional.of(player));

        playerService.updateIpGeoData(
            server,
            player.getMinecraftUuid().toString(),
            "127.0.0.1",
            Map.of("country", "CA", "region", "Ontario", "asn", "AS456", "proxy", false, "hosting", true)
        );

        verify(playerRepository).replaceIpAddresses(eq(server), eq(player));
        IPEntry updatedIp = player.getIpAddresses().get(0);
        assertEquals("CA", updatedIp.getCountry());
        assertEquals("Ontario", updatedIp.getRegion());
        assertEquals("AS456", updatedIp.getAsn());
        assertTrue(updatedIp.isHosting());
    }
}
