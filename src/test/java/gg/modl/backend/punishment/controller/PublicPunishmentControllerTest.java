package gg.modl.backend.punishment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.google.protobuf.Struct;
import gg.modl.backend.player.dto.response.AppealEligibility;
import gg.modl.backend.player.dto.response.AppealInfoView;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.proto.modl.v1.PublicPunishmentAppealInfoResponse;
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

        Map<String, Object> existingAppealData = new java.util.HashMap<>();
        existingAppealData.put("status", "rejected");
        existingAppealData.put("appealWorkflowStatus", "rejected");
        AppealInfoView info = new AppealInfoView(
            "punishment-1", null, null, null, false, false, null, existingAppealData, null);

        when(request.getAttribute(RequestAttribute.SERVER)).thenReturn(server);
        when(punishmentQueryService.getPublicPunishmentWithAppealEligibility(server, "punishment-1"))
            .thenReturn(Optional.of(new AppealEligibility.Eligible(info)));

        ResponseEntity<?> response = controller.getAppealInfo("punishment-1", request);

        assertEquals(200, response.getStatusCode().value());
        PublicPunishmentAppealInfoResponse body = (PublicPunishmentAppealInfoResponse) response.getBody();
        Struct existingAppeal = body.getExistingAppeal();
        assertEquals("rejected", existingAppeal.getFieldsOrThrow("status").getStringValue());
        assertEquals("rejected", existingAppeal.getFieldsOrThrow("appealWorkflowStatus").getStringValue());
    }
}
