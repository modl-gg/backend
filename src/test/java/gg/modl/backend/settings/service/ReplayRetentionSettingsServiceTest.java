package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.ReplayRetentionSettings;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplayRetentionSettingsServiceTest {
    private SettingsDocumentService settingsDocumentService;
    private ReplayRetentionSettingsService service;
    private Server server;

    @BeforeEach
    void setUp() {
        settingsDocumentService = mock(SettingsDocumentService.class);
        service = new ReplayRetentionSettingsService(settingsDocumentService, new ObjectMapper());
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    @Test
    void getReplayRetentionSettingsDefaultsToEnabledTenDays() {
        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));

        VersionedSettings<ReplayRetentionSettings> settings = service.getReplayRetentionSettingsState(server);

        assertEquals(0L, settings.version());
        assertEquals(true, settings.data().isEnabled());
        assertEquals(10, settings.data().getDays());
    }

    @Test
    void patchReplayRetentionSettingsCapsDaysAboveMaxWhenDisabled() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("enabled", false);
        current.put("days", 7);
        Date updatedAt = new Date(1000L);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("enabled", false);
        expected.put("days", 365);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(current, 2L, updatedAt, true));
        when(settingsDocumentService.saveRawState(server, "replayRetention", 2L, expected))
            .thenReturn(new SettingsDocumentService.RawSettingsState(expected, 3L, updatedAt, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.patchReplayRetentionSettings(server, 2L, null, 400);

        assertEquals(3L, settings.version());
        assertEquals(false, settings.data().isEnabled());
        assertEquals(365, settings.data().getDays());
    }

    @Test
    void getReplayRetentionSettingsMergesPartialDataWithDefaults() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("days", 14);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(current, 1L, null, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.getReplayRetentionSettingsState(server);

        assertEquals(true, settings.data().isEnabled());
        assertEquals(14, settings.data().getDays());
    }

    @Test
    void patchReplayRetentionSettingsRejectsEnabledZeroDays() {
        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.patchReplayRetentionSettings(server, 0L, true, 0)
        );

        assertEquals("Replay retention days must be at least 1 when enabled", exception.getMessage());
    }

    @Test
    void patchReplayRetentionSettingsRejectsEnabledNegativeDays() {
        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.patchReplayRetentionSettings(server, 0L, true, -5)
        );

        assertEquals("Replay retention days must be at least 1 when enabled", exception.getMessage());
    }

    @Test
    void patchReplayRetentionSettingsRejectsEnabledDaysAboveMax() {
        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));

        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> service.patchReplayRetentionSettings(server, 0L, true, 366)
        );

        assertEquals("Replay retention days must be at most 365", exception.getMessage());
    }

    @Test
    void patchReplayRetentionSettingsNormalizesDisabledZeroDaysToMinimum() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("enabled", false);
        expected.put("days", 1);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));
        when(settingsDocumentService.saveRawState(server, "replayRetention", 0L, expected))
            .thenReturn(new SettingsDocumentService.RawSettingsState(expected, 1L, null, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.patchReplayRetentionSettings(server, 0L, false, 0);

        assertEquals(false, settings.data().isEnabled());
        assertEquals(1, settings.data().getDays());
    }

    @Test
    void patchReplayRetentionSettingsNormalizesDisabledNegativeDaysToMinimum() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("enabled", false);
        expected.put("days", 1);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(new LinkedHashMap<>(), 0L, null, false));
        when(settingsDocumentService.saveRawState(server, "replayRetention", 0L, expected))
            .thenReturn(new SettingsDocumentService.RawSettingsState(expected, 1L, null, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.patchReplayRetentionSettings(server, 0L, false, -3);

        assertEquals(false, settings.data().isEnabled());
        assertEquals(1, settings.data().getDays());
    }

    @Test
    void getReplayRetentionSettingsNormalizesStoredZeroDaysToMinimum() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("enabled", true);
        current.put("days", 0);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(current, 1L, null, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.getReplayRetentionSettingsState(server);

        assertEquals(1, settings.data().getDays());
    }

    @Test
    void getReplayRetentionSettingsNormalizesStoredDaysAboveMaxToMaximum() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("enabled", true);
        current.put("days", 500);

        when(settingsDocumentService.getRawState(server, "replayRetention"))
            .thenReturn(new SettingsDocumentService.RawSettingsState(current, 1L, null, true));

        VersionedSettings<ReplayRetentionSettings> settings = service.getReplayRetentionSettingsState(server);

        assertEquals(365, settings.data().getDays());
    }
}
