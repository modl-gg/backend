package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ServerCredentialRepository;
import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.infrastructure.scheduling.SchedulerLeaseService;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.Settings;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiKeySettingsServiceTest {
    private static final String SETTINGS_TYPE = "apiKeys";

    private SettingsDocumentService settingsDocumentService;
    private IdGenerator idGenerator;
    private ServerLookupRepository serverLookupRepository;
    private ServerCredentialRepository serverCredentialRepository;
    private ApiKeySettingsService service;
    private Server server;

    @BeforeEach
    void setUp() {
        settingsDocumentService = mock(SettingsDocumentService.class);
        idGenerator = mock(IdGenerator.class);
        serverLookupRepository = mock(ServerLookupRepository.class);
        serverCredentialRepository = mock(ServerCredentialRepository.class);
        service = new ApiKeySettingsService(settingsDocumentService, idGenerator,
            serverLookupRepository, serverCredentialRepository, mock(SchedulerLeaseService.class));
        server = server();
    }

    private void stubState(Map<String, Object> data, long version) {
        when(settingsDocumentService.getRawState(server, SETTINGS_TYPE))
            .thenReturn(new SettingsDocumentService.RawSettingsState(data, version, null, true));
        when(settingsDocumentService.saveRawState(eq(server), eq(SETTINGS_TYPE), anyLong(), anyMap()))
            .thenAnswer(invocation -> new SettingsDocumentService.RawSettingsState(
                invocation.getArgument(3), version + 1, new Date(), true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePersistedData(long expectedVersion) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settingsDocumentService).saveRawState(eq(server), eq(SETTINGS_TYPE), eq(expectedVersion), captor.capture());
        return captor.getValue();
    }

    @Test
    void defaultGenerateSyncsCanonicalServerApiKey() {
        stubState(new LinkedHashMap<>(), 0L);
        when(idGenerator.generateToken()).thenReturn("generated");

        String apiKey = service.generateApiKey(server, "default");

        assertEquals("modl_generated", apiKey);
        assertEquals("modl_generated", capturePersistedData(0L).get("api_key"));
        verify(serverCredentialRepository).updateApiKey(server.getId(), "modl_generated");
    }

    @Test
    void nonCanonicalGenerateDoesNotTouchServerApiKey() {
        stubState(new LinkedHashMap<>(), 4L);
        when(idGenerator.generateToken()).thenReturn("generated");

        String apiKey = service.generateApiKey(server, "ticket");

        assertEquals("modl_generated", apiKey);
        assertEquals("modl_generated", capturePersistedData(4L).get("ticket_api_key"));
        verify(serverCredentialRepository, never()).updateApiKey(anyString(), anyString());
    }

    @Test
    void defaultDeleteClearsCanonicalServerApiKey() {
        stubState(new LinkedHashMap<>(Map.of("api_key", "old")), 3L);

        boolean deleted = service.deleteApiKey(server, "default");

        assertTrue(deleted);
        assertFalse(capturePersistedData(3L).containsKey("api_key"));
        verify(serverCredentialRepository).clearApiKey(server.getId());
    }

    @Test
    void deleteMissingKeyReturnsFalseWithoutWriting() {
        stubState(new LinkedHashMap<>(), 2L);

        boolean deleted = service.deleteApiKey(server, "ticket");

        assertFalse(deleted);
        verify(settingsDocumentService, never()).saveRawState(any(), anyString(), anyLong(), anyMap());
    }

    @Test
    void revealAndExistsReturnStoredKeysByType() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("api_key", "canonical");
        stored.put("ticket_api_key", "ticket");
        when(settingsDocumentService.getRawState(server, SETTINGS_TYPE))
            .thenReturn(new SettingsDocumentService.RawSettingsState(stored, 7L, null, true));

        assertEquals("canonical", service.revealApiKey(server, "default"));
        assertEquals("ticket", service.revealApiKey(server, "ticket"));
        assertNull(service.revealApiKey(server, "minecraft"));
        assertEquals("canonical", service.getApiKeyFromSettings(server));
        assertTrue(service.hasApiKey(server, "ticket"));
        assertFalse(service.hasApiKey(server, "minecraft"));
    }

    @Test
    void invalidLookupDoesNotScanAllTenantSettings() {
        when(serverLookupRepository.findByApiKey("invalid")).thenReturn(Optional.empty());

        assertNull(service.findServerByApiKey("invalid"));
        verify(serverLookupRepository, never()).findAll();
        verify(settingsDocumentService, never()).getRawState(any(), anyString());
    }

    @Test
    void generatePreservesUnknownKeysAndStampsVersionedEnvelopeOnLegacyDocument() {
        SettingsMongoRepository repository = mock(SettingsMongoRepository.class);
        SettingsDocumentService documentService = new SettingsDocumentService(repository);
        ApiKeySettingsService liveService = new ApiKeySettingsService(documentService, idGenerator,
            serverLookupRepository, serverCredentialRepository, mock(SchedulerLeaseService.class));

        Map<String, Object> legacyData = new LinkedHashMap<>();
        legacyData.put("api_key", "keepme");
        legacyData.put("legacyUnknownField", "survive");
        Settings legacy = new Settings("settings-id", SETTINGS_TYPE, legacyData);
        when(repository.findLatestByType(server, SETTINGS_TYPE, 2)).thenReturn(List.of(legacy));
        when(repository.updateWithVersionCheck(any(), anyString(), anyLong(), anyString(), anyMap(), anyLong(), any()))
            .thenReturn(true);
        when(idGenerator.generateToken()).thenReturn("newtoken");

        liveService.generateApiKey(server, "ticket");

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).updateWithVersionCheck(
            eq(server), eq("settings-id"), eq(0L), eq(SETTINGS_TYPE),
            dataCaptor.capture(), eq(1L), any(Date.class));

        Map<String, Object> persisted = dataCaptor.getValue();
        assertEquals("keepme", persisted.get("api_key"));
        assertEquals("survive", persisted.get("legacyUnknownField"));
        assertEquals("modl_newtoken", persisted.get("ticket_api_key"));
    }

    private static Server server() {
        Server server = new Server("Server", "server", "server_db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
