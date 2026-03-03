package gg.modl.backend.player;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.UsernameEntry;
import gg.modl.backend.player.dto.response.PlayerSearchResult;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceSearchRankingTest {

    @Mock
    private DynamicMongoTemplateProvider mongoProvider;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private MongoTemplate template;

    @Mock
    private Server server;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(mongoProvider, statusCalculator, punishmentTypeService);
        when(server.getDatabaseName()).thenReturn("test_db");
        when(mongoProvider.getFromDatabaseName("test_db")).thenReturn(template);
    }

    @Test
    void exactCurrentUsernamePreferredOverPrefixedPartialMatch() {
        UUID javaUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bedrockUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        Player javaPlayer = player(javaUuid, List.of("UrgedRook8642"), null);
        Player bedrockPlayer = player(bedrockUuid, List.of(".UrgedRook8642"), null);

        when(template.find(any(Query.class), eq(Player.class), eq(CollectionName.PLAYERS)))
                .thenReturn(List.of(bedrockPlayer, javaPlayer));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, "UrgedRook8642");

        assertEquals(2, results.size());
        assertEquals(javaUuid.toString(), results.get(0).uuid());
        assertEquals("UrgedRook8642", results.get(0).username());
    }

    @Test
    void historicalExactMatchPreferredOverCurrentPrefixMatch() {
        UUID renamedUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID prefixedUuid = UUID.fromString("44444444-4444-4444-4444-444444444444");

        Player renamedPlayer = player(renamedUuid, List.of("Name123", "RenamedPlayer"), null);
        Player prefixedPlayer = player(prefixedUuid, List.of(".Name123"), null);

        when(template.find(any(Query.class), eq(Player.class), eq(CollectionName.PLAYERS)))
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

        when(template.find(any(Query.class), eq(Player.class), eq(CollectionName.PLAYERS)))
                .thenReturn(List.of(partialPlayer, exactPlayer));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, "name123");

        assertEquals(2, results.size());
        assertEquals(exactUuid.toString(), results.get(0).uuid());
        assertEquals("NAME123", results.get(0).username());
    }

    @Test
    void uuidSearchNormalizesCaseBeforeQuerying() {
        UUID playerUuid = UUID.fromString("77777777-7777-7777-7777-777777777777");
        String uppercaseUuid = playerUuid.toString().toUpperCase(Locale.ROOT);

        Player player = player(playerUuid, List.of("SomePlayer"), Date.from(Instant.now()));
        when(template.find(any(Query.class), eq(Player.class), eq(CollectionName.PLAYERS)))
                .thenReturn(List.of(player));

        List<PlayerSearchResult> results = playerService.searchPlayers(server, uppercaseUuid);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(queryCaptor.capture(), eq(Player.class), eq(CollectionName.PLAYERS));

        assertFalse(results.isEmpty());
        assertEquals(playerUuid.toString(), results.get(0).uuid());
        assertEquals(
                playerUuid.toString(),
                queryCaptor.getValue().getQueryObject().getString("minecraftUuid")
        );
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
}
