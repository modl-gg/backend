package gg.modl.backend.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
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
            .id(UUID.randomUUID().toString())
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("OldName", new Date(1_000L)))))
            .ipAddresses(new ArrayList<>())
            .notes(new ArrayList<>())
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        when(playerRepository.findByMinecraftUuid(server, playerUuid)).thenReturn(Optional.of(player));

        PlayerService.LoginResult result = playerService.loginPlayer(
            server,
            playerUuid,
            "NewName",
            "127.0.0.1",
            Map.of("country", "US", "region", "Virginia", "asn", "AS123", "proxy", true, "hosting", false),
            "skin-hash",
            "hub"
        );

        assertNotNull(result);
        Player updated = result.player();
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
    void updateIpGeoDataLowercasesUuidBeforeQueryingRepository() {
        when(playerRepository.findByMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
            .thenReturn(Optional.empty());

        playerService.updateIpGeoData(
            server,
            "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
            "1.2.3.4",
            Map.of()
        );

        verify(playerRepository).findByMinecraftUuid(server, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void updateIpGeoDataMutatesExistingIpEntryAndPersistsThroughRepository() {
        Player player = Player.builder()
            .id(UUID.randomUUID().toString())
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

    @Test
    void loginPlayer_backfills_missing_date_on_current_username() {
        UUID playerUuid = UUID.randomUUID();
        Player player = Player.builder()
            .id(UUID.randomUUID().toString())
            .minecraftUuid(playerUuid)
            .usernames(new ArrayList<>(List.of(new UsernameEntry("modltarget", null))))
            .ipAddresses(new ArrayList<>())
            .notes(new ArrayList<>())
            .punishments(new ArrayList<>())
            .data(new HashMap<>())
            .build();

        when(playerRepository.findByMinecraftUuid(server, playerUuid)).thenReturn(Optional.of(player));

        PlayerService.LoginResult result = playerService.loginPlayer(
            server,
            playerUuid,
            "modltarget",
            "127.0.0.1",
            null,
            null,
            "hub"
        );

        assertNotNull(result);
        verify(playerRepository).updateLoginState(eq(server), eq(player));
        assertEquals(1, player.getUsernames().size());
        assertEquals("modltarget", player.getUsernames().get(0).username());
        assertNotNull(player.getUsernames().get(0).date());
    }

    @Test
    void createPlayer_assigns_uuid_v4_document_id() {
        UUID playerUuid = UUID.randomUUID();
        when(playerRepository.saveEntity(eq(server), any(Player.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));

        Player created = playerService.createPlayer(server, playerUuid, "ApiCreatedPlayer");

        ArgumentCaptor<Player> savedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveEntity(eq(server), savedPlayerCaptor.capture());

        Player savedPlayer = savedPlayerCaptor.getValue();
        assertEquals(playerUuid, savedPlayer.getMinecraftUuid());
        assertEquals("ApiCreatedPlayer", savedPlayer.getUsernames().get(0).username());
        assertUuidV4(savedPlayer.getId());
        assertEquals(savedPlayer.getId(), created.getId());
    }

    @Test
    void loginPlayer_creates_new_player_with_uuid_v4_document_id_on_first_login() {
        UUID playerUuid = UUID.randomUUID();
        when(playerRepository.findByMinecraftUuid(server, playerUuid)).thenReturn(Optional.empty());
        when(playerRepository.saveEntity(eq(server), any(Player.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));

        PlayerService.LoginResult result = playerService.loginPlayer(
            server,
            playerUuid,
            "FirstJoinPlayer",
            "127.0.0.1",
            null,
            "skin-hash",
            "hub"
        );

        ArgumentCaptor<Player> savedPlayerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveEntity(eq(server), savedPlayerCaptor.capture());

        Player savedPlayer = savedPlayerCaptor.getValue();
        assertNotNull(result);
        assertEquals(savedPlayer.getId(), result.player().getId());
        assertUuidV4(savedPlayer.getId());
        assertEquals(playerUuid, savedPlayer.getMinecraftUuid());
        assertEquals("FirstJoinPlayer", savedPlayer.getUsernames().get(0).username());
    }

    private static void assertUuidV4(String value) {
        UUID parsed = UUID.fromString(value);
        assertEquals(4, parsed.version());
        assertEquals(2, parsed.variant());
    }
}
