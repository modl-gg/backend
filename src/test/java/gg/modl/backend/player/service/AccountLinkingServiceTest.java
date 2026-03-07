package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLinkingServiceTest {

    @Mock
    private PlayerMongoRepository playerRepository;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    private AccountLinkingService accountLinkingService;

    @BeforeEach
    void setUp() {
        accountLinkingService = new AccountLinkingService(playerRepository, statusCalculator, punishmentTypeService);
    }

    @Test
    void findAndLinkAccountsPersistsForwardAndReverseLinks() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        UUID playerUuid = UUID.randomUUID();
        UUID linkedUuid = UUID.randomUUID();
        Date now = new Date();

        Player player = Player.builder()
                .id("player-1")
                .minecraftUuid(playerUuid)
                .ipAddresses(new ArrayList<>(List.of(IPEntry.builder()
                        .ipAddress("1.1.1.1")
                        .proxy(false)
                        .firstLogin(now)
                        .logins(new ArrayList<>(List.of(now)))
                        .build())))
                .build();
        Player linkedPlayer = Player.builder()
                .id("player-2")
                .minecraftUuid(linkedUuid)
                .ipAddresses(new ArrayList<>(List.of(IPEntry.builder()
                        .ipAddress("1.1.1.1")
                        .proxy(false)
                        .firstLogin(now)
                        .logins(new ArrayList<>(List.of(now)))
                        .build())))
                .build();

        when(playerRepository.findOne(any(Server.class), any()))
                .thenReturn(Optional.of(player), Optional.of(linkedPlayer));
        when(playerRepository.find(any(Server.class), any())).thenReturn(List.of(player, linkedPlayer));
        when(playerRepository.snapshot(any(Player.class))).thenAnswer(invocation -> {
            Player value = invocation.getArgument(0);
            return Player.builder().id(value.getId()).minecraftUuid(value.getMinecraftUuid()).build();
        });

        AccountLinkingService.LinkingResult result = accountLinkingService.findAndLinkAccounts(server, playerUuid);

        assertTrue(result.success());
        assertEquals(1, result.linkedAccountsFound());

        ArgumentCaptor<Player> updatedPlayers = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository, times(2)).saveChanges(any(Server.class), any(Player.class), updatedPlayers.capture());
        List<Player> savedPlayers = updatedPlayers.getAllValues();
        assertTrue(savedPlayers.stream().anyMatch(saved -> containsLink(saved, linkedUuid.toString())));
        assertTrue(savedPlayers.stream().anyMatch(saved -> containsLink(saved, playerUuid.toString())));
    }

    @SuppressWarnings("unchecked")
    private boolean containsLink(Player player, String expectedUuid) {
        Object rawLinks = player.getData() != null ? player.getData().get("linkedAccounts") : null;
        if (!(rawLinks instanceof List<?> links)) {
            return false;
        }
        return ((List<String>) links).contains(expectedUuid);
    }
}