package gg.modl.backend.alert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.alert.data.SystemAlert;
import gg.modl.backend.alert.data.SystemAlertAudience;
import gg.modl.backend.alert.data.SystemAlertSeverity;
import gg.modl.backend.database.mongo.repository.SystemAlertMongoRepository;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemAlertServiceTest {

    @Mock
    private SystemAlertMongoRepository alertRepository;

    private SystemAlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new SystemAlertService(alertRepository);
    }

    @Test
    void getVisibleAlertsKeepsExpiredAlertsOutOfPanelResults() {
        Date now = new Date();
        SystemAlert activeAlert = alert("visible", SystemAlertAudience.ALL_PANEL_USERS, new Date(now.getTime() + 60_000));
        SystemAlert expiredAlert = alert("expired", SystemAlertAudience.ALL_PANEL_USERS, new Date(now.getTime() - 60_000));

        when(alertRepository.findVisible(now)).thenReturn(List.of(activeAlert));

        List<SystemAlert> alerts = alertService.getVisibleAlerts(false, now);

        assertEquals(List.of(activeAlert), alerts);
    }

    @Test
    void getVisibleAlertsFiltersSuperAdminOnlyAlertsForRegularPanelUsers() {
        Date now = new Date();
        SystemAlert publicAlert = alert("public", SystemAlertAudience.ALL_PANEL_USERS, new Date(now.getTime() + 60_000));
        SystemAlert superAdminAlert = alert("private", SystemAlertAudience.SUPER_ADMINS_ONLY, new Date(now.getTime() + 60_000));

        when(alertRepository.findVisible(now)).thenReturn(List.of(publicAlert, superAdminAlert));

        assertEquals(List.of(publicAlert), alertService.getVisibleAlerts(false, now));
        assertEquals(List.of(publicAlert, superAdminAlert), alertService.getVisibleAlerts(true, now));
    }

    @Test
    void updateAlertRejectsBlankMessage() {
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000);

        assertThrows(IllegalArgumentException.class, () -> alertService.updateAlert(
            "alert-1",
            "   ",
            SystemAlertSeverity.WARNING,
            SystemAlertAudience.ALL_PANEL_USERS,
            true,
            expiresAt,
            "admin@example.com"
        ));
    }

    @Test
    void updateAlertClearsExpiryWhenPresentAndNull() {
        SystemAlert alert = alert("alert-1", SystemAlertAudience.ALL_PANEL_USERS, null);
        when(alertRepository.updateAlert(
            eq("alert-1"), any(), any(), any(), anyBoolean(), any(), any(), any()
        )).thenReturn(Optional.of(alert));

        alertService.updateAlert(
            "alert-1",
            "still valid",
            SystemAlertSeverity.WARNING,
            SystemAlertAudience.ALL_PANEL_USERS,
            true,
            null,
            "admin@example.com"
        );

        ArgumentCaptor<Boolean> presentCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Date> expiryCaptor = ArgumentCaptor.forClass(Date.class);
        verify(alertRepository).updateAlert(
            eq("alert-1"), any(), any(), any(), presentCaptor.capture(), expiryCaptor.capture(), any(), eq("admin@example.com")
        );
        assertTrue(presentCaptor.getValue());
        assertNull(expiryCaptor.getValue());
    }

    @Test
    void updateAlertLeavesExpiryWhenAbsent() {
        SystemAlert alert = alert("alert-1", SystemAlertAudience.ALL_PANEL_USERS, new Date());
        when(alertRepository.updateAlert(
            eq("alert-1"), any(), any(), any(), anyBoolean(), any(), any(), any()
        )).thenReturn(Optional.of(alert));

        alertService.updateAlert(
            "alert-1",
            "still valid",
            SystemAlertSeverity.WARNING,
            SystemAlertAudience.ALL_PANEL_USERS,
            false,
            null,
            "admin@example.com"
        );

        ArgumentCaptor<Boolean> presentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(alertRepository).updateAlert(
            eq("alert-1"), any(), any(), any(), presentCaptor.capture(), any(), any(), eq("admin@example.com")
        );
        assertEquals(Boolean.FALSE, presentCaptor.getValue());
    }

    private static SystemAlert alert(String id, SystemAlertAudience audience, Date expiresAt) {
        return SystemAlert.builder()
            .id(id)
            .message("Alert " + id)
            .severity(SystemAlertSeverity.BASIC)
            .audience(audience)
            .expiresAt(expiresAt)
            .createdAt(new Date(0))
            .updatedAt(new Date(0))
            .createdBy("admin@example.com")
            .build();
    }
}
