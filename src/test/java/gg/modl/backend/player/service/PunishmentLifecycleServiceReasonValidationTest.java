package gg.modl.backend.player.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.log.service.LogService;
import gg.modl.backend.player.dto.request.CreatePunishmentRequest;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.settings.service.WebhookSettingsService;
import gg.modl.backend.ticket.service.TicketService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PunishmentLifecycleServiceReasonValidationTest {

    private static final int BAN_ORDINAL = 2;

    private Server server;
    private PlayerMongoRepository playerRepository;
    private PunishmentLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        playerRepository = mock(PlayerMongoRepository.class);
        lifecycleService = new PunishmentLifecycleService(
            playerRepository,
            mock(PunishmentMongoRepository.class),
            mock(TicketService.class),
            mock(PlayerStatusCalculator.class),
            mock(PunishmentTypeService.class),
            mock(OffenderThresholdSettingsService.class),
            mock(PunishmentDurationCalculator.class),
            mock(IssuerNameResolver.class),
            mock(PunishmentQueryService.class),
            mock(PermissionService.class),
            mock(RoleAuthorization.class),
            mock(WebhookSettingsService.class),
            mock(PunishmentRealtimePublisher.class),
            mock(LogService.class)
        );
    }

    @Test
    void rejectsReasonLongerThanLimit() {
        CreatePunishmentRequest request = requestWithReason(overlongReason(), null);

        ValidationException exception = assertThrows(ValidationException.class,
            () -> lifecycleService.createPunishment(server, UUID.randomUUID(), request));

        assertEquals("reason must be at most "
            + RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH + " characters", exception.getMessage());
        verifyNoInteractions(playerRepository);
    }

    @Test
    void rejectsDataReasonLongerThanLimit() {
        CreatePunishmentRequest request = requestWithReason(null, Map.of("reason", overlongReason()));

        assertThrows(ValidationException.class,
            () -> lifecycleService.createPunishment(server, UUID.randomUUID(), request));

        verifyNoInteractions(playerRepository);
    }

    @Test
    void acceptsReasonAtLimit() {
        when(playerRepository.findByMinecraftUuid(any(), anyString())).thenReturn(Optional.empty());
        String reasonAtLimit = "r".repeat(RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH);
        CreatePunishmentRequest request = requestWithReason(reasonAtLimit, null);

        assertThrows(ResourceNotFoundException.class,
            () -> lifecycleService.createPunishment(server, UUID.randomUUID(), request));
    }

    private String overlongReason() {
        return "r".repeat(RequestValidationLimits.PLAYER_PUNISHMENT_REASON_MAX_LENGTH + 1);
    }

    private CreatePunishmentRequest requestWithReason(String reason, Map<String, Object> data) {
        return new CreatePunishmentRequest(
            "Staff", null, BAN_ORDINAL, null, null, null, null, null, data, reason, null);
    }
}
