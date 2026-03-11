package gg.modl.backend.punishment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import jakarta.servlet.http.HttpServletRequest;
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
    private HttpServletRequest request;

    private PublicPunishmentController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicPunishmentController(punishmentQueryService);
    }

    @Test
    void getAppealInfoUsesWorkflowStatusForExistingAppeals() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);

        Map<String, Object> serviceResult = new java.util.HashMap<>();
        serviceResult.put("id", "punishment-1");
        Map<String, Object> existingAppealData = new java.util.HashMap<>();
        existingAppealData.put("status", "rejected");
        existingAppealData.put("appealWorkflowStatus", "rejected");
        serviceResult.put("existingAppeal", existingAppealData);

        when(request.getAttribute(RequestAttribute.SERVER)).thenReturn(server);
        when(punishmentQueryService.getPublicPunishmentWithAppealEligibility(server, "punishment-1"))
            .thenReturn(Optional.of(serviceResult));

        ResponseEntity<?> response = controller.getAppealInfo("punishment-1", request);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> existingAppeal = (Map<?, ?>) body.get("existingAppeal");
        assertEquals("rejected", existingAppeal.get("status"));
        assertEquals("rejected", existingAppeal.get("appealWorkflowStatus"));
    }
}
