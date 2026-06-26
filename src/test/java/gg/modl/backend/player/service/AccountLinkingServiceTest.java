package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.IPEntry;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        when(playerRepository.findByMinecraftUuid(server, playerUuid)).thenReturn(Optional.of(player));
        when(playerRepository.findByIpAddresses(server, Set.of("1.1.1.1"))).thenReturn(List.of(player, linkedPlayer));

        AccountLinkingService.LinkingResult result = accountLinkingService.findAndLinkAccounts(server, playerUuid);

        assertTrue(result.success());
        assertEquals(1, result.linkedAccountsFound());
        // Atomic server-side merge: forward link on the player, reverse link on the partner.
        verify(playerRepository).addLinkedAccounts(
            eq(server),
            eq(playerUuid.toString()),
            argThat(set -> set.contains(linkedUuid.toString())),
            any(Date.class));
        verify(playerRepository).addLinkedAccounts(
            eq(server),
            eq(linkedUuid.toString()),
            argThat(set -> set.contains(playerUuid.toString())),
            any(Date.class));
    }
}