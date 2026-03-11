package gg.modl.backend.punishment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PublicPunishmentControllerTest {

    @Mock
    private PunishmentQueryService punishmentQueryService;

    @Mock
    private PunishmentTypeService punishmentTypeService;

    @Mock
    private AppealService appealService;

    @Mock
    private PlayerStatusCalculator statusCalculator;

    @Mock
    private HttpServletRequest request;

    private PublicPunishmentController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPunishmentController(
                punishmentQueryService,
                punishmentTypeService,
                appealService,
                statusCalculator
        );
    }

    @Test
    void getAppealInfoUsesWorkflowStatusForExistingAppeals() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        Date now = new Date();
        PunishmentResponse punishment = new PunishmentResponse(
                "punishment-1",
                "ban",
                1,
                "Moderator",
                now,
                now,
                true,
                "Rule violation",
                "high",
                "active",
                true,
                null,
                "uuid-1",
                "PlayerOne",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        TicketResponse appeal = new TicketResponse(
                "APPEAL-123",
                "appeal",
                "Ban Appeal",
                "Appeal subject",
                "closed",
                "rejected",
                "PlayerOne",
                "uuid-1",
                "PlayerOne",
                null,
                null,
                now,
                true,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                null,
                false,
                false
        );

        when(request.getAttribute(RequestAttribute.SERVER)).thenReturn(server);
        when(punishmentQueryService.getPunishmentById(server, "punishment-1")).thenReturn(Optional.of(punishment));
        when(appealService.getAppealsByPunishment(server, "punishment-1")).thenReturn(List.of(appeal));
        when(punishmentTypeService.getPunishmentTypeByOrdinal(server, 1)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getAppealInfo("punishment-1", request);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> existingAppeal = (Map<?, ?>) body.get("existingAppeal");
        assertEquals("rejected", existingAppeal.get("status"));
        assertEquals("rejected", existingAppeal.get("appealWorkflowStatus"));
    }
}
