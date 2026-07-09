package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.AIModerationSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AIModerationSettingsServiceTest {
    private static final String SETTINGS_TYPE = "aiModerationSettings";

    private SettingsDocumentService settingsDocumentService;
    private AIModerationSettingsService service;
    private Server server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        settingsDocumentService = mock(SettingsDocumentService.class);
        objectMapper = new ObjectMapper();
        service = new AIModerationSettingsService(settingsDocumentService, objectMapper);
        server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
    }

    private Map<String, Object> storedSettings(String... configIds) {
        Map<String, Object> configs = new LinkedHashMap<>();
        for (String configId : configIds) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("id", configId);
            config.put("name", "Config " + configId);
            config.put("aiDescription", "Description " + configId);
            config.put("enabled", true);
            configs.put(configId, config);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enableAIReview", true);
        data.put("enableAutomatedActions", true);
        data.put("aiPunishmentConfigs", configs);
        return data;
    }

    private AIModerationSettings.AIPunishmentConfig config(String id) {
        return AIModerationSettings.AIPunishmentConfig.builder()
            .id(id)
            .name("Config " + id)
            .aiDescription("Description " + id)
            .enabled(true)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePersistedData(long expectedVersion) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settingsDocumentService).saveRawState(eq(server), eq(SETTINGS_TYPE), eq(expectedVersion), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> persistedConfigs(Map<String, Object> persisted) {
        return (Map<String, Object>) persisted.get("aiPunishmentConfigs");
    }

    @Test
    void updateDeletesConfigsAbsentFromRequest() {
        when(settingsDocumentService.getRawState(server, SETTINGS_TYPE))
            .thenReturn(new SettingsDocumentService.RawSettingsState(storedSettings("6", "7"), 3L, null, true));
        when(settingsDocumentService.saveRawState(eq(server), eq(SETTINGS_TYPE), anyLong(), anyMap()))
            .thenAnswer(invocation -> new SettingsDocumentService.RawSettingsState(invocation.getArgument(3), 4L, null, true));

        AIModerationSettings result = service.updateAIModerationSettings(server, AIModerationSettings.builder()
            .enableAIReview(true)
            .enableAutomatedActions(true)
            .aiPunishmentConfigs(Map.of("6", config("6")))
            .build());

        Map<String, Object> persisted = capturePersistedData(3L);
        Map<String, Object> persistedConfigs = persistedConfigs(persisted);
        assertEquals(1, persistedConfigs.size());
        assertTrue(persistedConfigs.containsKey("6"));
        assertFalse(persistedConfigs.containsKey("7"));
        assertEquals(true, persisted.get("enableAIReview"));
        assertEquals(true, persisted.get("enableAutomatedActions"));
        assertEquals(1, result.getAiPunishmentConfigs().size());
        assertTrue(result.getAiPunishmentConfigs().containsKey("6"));
    }

    @Test
    void updateWithEmptyConfigsDeletesAllConfigs() {
        when(settingsDocumentService.getRawState(server, SETTINGS_TYPE))
            .thenReturn(new SettingsDocumentService.RawSettingsState(storedSettings("6", "7"), 5L, null, true));
        when(settingsDocumentService.saveRawState(eq(server), eq(SETTINGS_TYPE), anyLong(), anyMap()))
            .thenAnswer(invocation -> new SettingsDocumentService.RawSettingsState(invocation.getArgument(3), 6L, null, true));

        AIModerationSettings result = service.updateAIModerationSettings(server, AIModerationSettings.builder()
            .enableAIReview(true)
            .enableAutomatedActions(false)
            .aiPunishmentConfigs(Map.of())
            .build());

        Map<String, Object> persisted = capturePersistedData(5L);
        assertTrue(persistedConfigs(persisted).isEmpty());
        assertEquals(true, persisted.get("enableAIReview"));
        assertEquals(false, persisted.get("enableAutomatedActions"));
        assertTrue(result.getAiPunishmentConfigs().isEmpty());
    }

    @Test
    void updatePersistsTogglesAlongsideReplacedConfigs() {
        when(settingsDocumentService.getRawState(server, SETTINGS_TYPE))
            .thenReturn(new SettingsDocumentService.RawSettingsState(storedSettings("6"), 1L, null, true));
        when(settingsDocumentService.saveRawState(eq(server), eq(SETTINGS_TYPE), anyLong(), anyMap()))
            .thenAnswer(invocation -> new SettingsDocumentService.RawSettingsState(invocation.getArgument(3), 2L, null, true));

        AIModerationSettings result = service.updateAIModerationSettings(server, AIModerationSettings.builder()
            .enableAIReview(false)
            .enableAutomatedActions(true)
            .aiPunishmentConfigs(Map.of("6", config("6"), "9", config("9")))
            .build());

        Map<String, Object> persisted = capturePersistedData(1L);
        Map<String, Object> persistedConfigs = persistedConfigs(persisted);
        assertEquals(2, persistedConfigs.size());
        assertTrue(persistedConfigs.containsKey("9"));
        assertEquals(false, persisted.get("enableAIReview"));
        assertEquals(true, persisted.get("enableAutomatedActions"));
        assertFalse(result.isEnableAIReview());
        assertTrue(result.isEnableAutomatedActions());
        assertEquals(2, result.getAiPunishmentConfigs().size());
    }
}
