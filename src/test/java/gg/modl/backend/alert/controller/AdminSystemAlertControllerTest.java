package gg.modl.backend.alert.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.alert.service.SystemAlertService;
import gg.modl.proto.modl.v1.AdminSystemAlertResponse;
import gg.modl.proto.modl.v1.CreateSystemAlertRequest;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminSystemAlertControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAlertReturnsAdminResponseProtoNotMongoDocument() {
        SystemAlertService alertService = mock(SystemAlertService.class);
        AdminSystemAlertController controller = new AdminSystemAlertController(alertService);
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000);
        CreateSystemAlertRequest request = CreateSystemAlertRequest.newBuilder()
            .setMessage("Maintenance soon")
            .setSeverity(SystemAlertSeverity.WARNING.name())
            .setAudience(SystemAlertAudience.ALL_PANEL_USERS.name())
            .setExpiresAt(expiresAt.getTime())
            .build();
        when(alertService.createAlert(
            "Maintenance soon",
            SystemAlertSeverity.WARNING,
            SystemAlertAudience.ALL_PANEL_USERS,
            expiresAt,
            "admin@example.com"
        )).thenReturn(alert("alert-1", expiresAt));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin@example.com", null));

        ResponseEntity<AdminSystemAlertResponse> response = controller.createAlert(request);

        AdminSystemAlertResponse body = assertInstanceOf(AdminSystemAlertResponse.class, response.getBody());
        assertEquals("alert-1", body.getId());
        assertEquals(SystemAlertSeverity.WARNING.name(), body.getSeverity());
    }

    private static SystemAlert alert(String id, Date expiresAt) {
        return SystemAlert.builder()
            .id(id)
            .message("Maintenance soon")
            .severity(SystemAlertSeverity.WARNING)
            .audience(SystemAlertAudience.ALL_PANEL_USERS)
            .expiresAt(expiresAt)
            .createdAt(new Date(0))
            .updatedAt(new Date(0))
            .createdBy("admin@example.com")
            .updatedBy("admin@example.com")
            .build();
    }
}
