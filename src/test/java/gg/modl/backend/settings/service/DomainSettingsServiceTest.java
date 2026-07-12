package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.database.mongo.repository.SettingsMongoRepository;
import gg.modl.backend.infrastructure.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.data.Settings;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DomainSettingsServiceTest {
    private static final String SETTINGS_TYPE = "domain";

    private SettingsDocumentService settingsDocumentService;
    private ServerCustomDomainRepository serverRepository;
    private CloudflareClient cloudflareClient;
    private DynamicCorsConfigurationSource corsConfigurationSource;
    private CustomDomainAccessService customDomainAccessService;
    private DomainSettingsService domainSettingsService;

    @BeforeEach
    void setUp() {
        settingsDocumentService = mock(SettingsDocumentService.class);
        serverRepository = mock(ServerCustomDomainRepository.class);
        cloudflareClient = mock(CloudflareClient.class);
        corsConfigurationSource = mock(DynamicCorsConfigurationSource.class);
        customDomainAccessService = mock(CustomDomainAccessService.class);
        domainSettingsService = new DomainSettingsService(
            settingsDocumentService,
            serverRepository,
            cloudflareClient,
            corsConfigurationSource,
            customDomainAccessService
        );
    }

    private void stubState(Map<String, Object> data, long version) {
        when(settingsDocumentService.getRawState(any(), eq(SETTINGS_TYPE)))
            .thenReturn(new SettingsDocumentService.RawSettingsState(data, version, null, true));
        when(settingsDocumentService.saveRawState(any(), eq(SETTINGS_TYPE), anyLong(), anyMap()))
            .thenAnswer(invocation -> new SettingsDocumentService.RawSettingsState(
                invocation.getArgument(3), version + 1, new Date(), true));
    }

    @Test
    void configureDomainRejectsPlatformDomainBeforeCloudflare() {
        assertThrows(ValidationException.class, () -> domainSettingsService.configureDomain(server(), "panel.modl.gg"));

        verify(cloudflareClient, never()).findCustomHostnameByName("panel.modl.gg");
    }

    @Test
    void configureDomainRejectsIpLiteralBeforeCloudflare() {
        assertThrows(ValidationException.class, () -> domainSettingsService.configureDomain(server(), "127.0.0.1"));

        verify(cloudflareClient, never()).findCustomHostnameByName("127.0.0.1");
    }

    @Test
    void configureDomainCanonicalizesBeforeCloudflare() {
        stubState(new LinkedHashMap<>(), 0L);
        when(cloudflareClient.findCustomHostnameByName("example.org")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("example.org"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("cf-id", "example.org", "pending", null, null, null));

        domainSettingsService.configureDomain(server(), "Example.ORG");

        verify(cloudflareClient).findCustomHostnameByName("example.org");
        verify(cloudflareClient).createCustomHostname("example.org");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settingsDocumentService).saveRawState(any(), eq(SETTINGS_TYPE), eq(0L), captor.capture());
        assertEquals("example.org", captor.getValue().get("customDomain"));
        assertEquals("cf-id", captor.getValue().get("cloudflareHostnameId"));
    }

    @Test
    void getDomainSettingsReturnsStoredDomainAndStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("domain", "custom.example.com");
        status.put("status", "active");
        status.put("cnameConfigured", true);
        status.put("sslStatus", "active");
        status.put("lastChecked", "2026-01-01T00:00:00Z");
        status.put("error", null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customDomain", "custom.example.com");
        data.put("cloudflareHostnameId", "cf-id");
        data.put("status", status);
        when(settingsDocumentService.getRawState(any(), eq(SETTINGS_TYPE)))
            .thenReturn(new SettingsDocumentService.RawSettingsState(data, 5L, null, true));
        when(customDomainAccessService.canManageCustomDomain(any())).thenReturn(true);

        DomainSettings result = domainSettingsService.getDomainSettings(server(), "custom.example.com");

        assertEquals("custom.example.com", result.getCustomDomain());
        assertTrue(result.isAccessingFromCustomDomain());
        assertEquals("https://tenant.modl.gg", result.getModlSubdomainUrl());
        assertTrue(result.isCanManageCustomDomain());
        assertEquals("active", result.getStatus().getStatus());
        assertEquals("active", result.getStatus().getSslStatus());
        assertTrue(result.getStatus().isCnameConfigured());
        assertEquals("custom.example.com", result.getStatus().getDomain());
    }

    @Test
    void getDomainSettingsReturnsDefaultsWhenUnconfigured() {
        stubState(new LinkedHashMap<>(), 0L);
        when(customDomainAccessService.canManageCustomDomain(any())).thenReturn(false);

        DomainSettings result = domainSettingsService.getDomainSettings(server(), "custom.example.com");

        assertNull(result.getCustomDomain());
        assertNull(result.getStatus());
        assertFalse(result.isAccessingFromCustomDomain());
        assertEquals("https://tenant.modl.gg", result.getModlSubdomainUrl());
        assertFalse(result.isCanManageCustomDomain());
    }

    @Test
    void verifyPreservesUnknownKeysAndStampsVersionedEnvelopeOnLegacyDocument() {
        SettingsMongoRepository repository = mock(SettingsMongoRepository.class);
        SettingsDocumentService documentService = new SettingsDocumentService(repository);
        DomainSettingsService liveService = new DomainSettingsService(
            documentService, serverRepository, cloudflareClient, corsConfigurationSource, customDomainAccessService);
        Server server = server();

        Map<String, Object> legacyStatus = new LinkedHashMap<>();
        legacyStatus.put("domain", "example.org");
        legacyStatus.put("status", "pending");
        Map<String, Object> legacyData = new LinkedHashMap<>();
        legacyData.put("customDomain", "example.org");
        legacyData.put("cloudflareHostnameId", "cf-id");
        legacyData.put("status", legacyStatus);
        legacyData.put("legacyUnknownField", "survive");
        Settings legacy = new Settings("settings-id", SETTINGS_TYPE, legacyData);
        when(repository.findLatestByType(server, SETTINGS_TYPE, 2)).thenReturn(List.of(legacy));
        when(repository.updateWithVersionCheck(any(), anyString(), anyLong(), anyString(), anyMap(), anyLong(), any()))
            .thenReturn(true);
        when(cloudflareClient.getCustomHostname("cf-id")).thenReturn(new CloudflareClient.CustomHostnameResult(
            "cf-id", "example.org", "active",
            new CloudflareClient.CustomHostnameResult.SslStatus("active", null, null), null, null));

        DomainSettings result = liveService.verifyDomain(server, "example.org");

        assertEquals("active", result.getStatus().getStatus());

        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).updateWithVersionCheck(
            eq(server), eq("settings-id"), eq(0L), eq(SETTINGS_TYPE),
            dataCaptor.capture(), eq(1L), any(Date.class));

        Map<String, Object> persisted = dataCaptor.getValue();
        assertEquals("survive", persisted.get("legacyUnknownField"));
        assertEquals("example.org", persisted.get("customDomain"));
        assertEquals("cf-id", persisted.get("cloudflareHostnameId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> persistedStatus = (Map<String, Object>) persisted.get("status");
        assertEquals("active", persistedStatus.get("status"));
    }

    @Test
    void removeDeletesDocumentAndClearsServerFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customDomain", "example.org");
        data.put("cloudflareHostnameId", "cf-id");
        when(settingsDocumentService.getRawState(any(), eq(SETTINGS_TYPE)))
            .thenReturn(new SettingsDocumentService.RawSettingsState(data, 4L, null, true));
        when(cloudflareClient.deleteCustomHostname("cf-id")).thenReturn(true);

        domainSettingsService.removeDomain(server());

        verify(cloudflareClient).deleteCustomHostname("cf-id");
        verify(settingsDocumentService).deleteState(any(), eq(SETTINGS_TYPE));
        verify(serverRepository).clearCustomDomain("server-id");
        verify(corsConfigurationSource).invalidateCache("example.org");
    }

    private static Server server() {
        Server server = new Server("server", "tenant", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        return server;
    }
}
