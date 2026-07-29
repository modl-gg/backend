package gg.modl.backend.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.cloudflare.config.CloudflareConfiguration;
import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ServiceUnavailableException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.data.DomainSettings;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class DomainSettingsServiceTest {
    private static final String SETTINGS_TYPE = "domain";

    private SettingsDocumentService settingsDocumentService;
    private ServerCustomDomainRepository serverRepository;
    private CloudflareClient cloudflareClient;
    private CloudflareConfiguration cloudflareConfiguration;
    private CustomDomainAccessService customDomainAccessService;
    private CustomDomainStateWriter stateWriter;
    private DomainSettingsService domainSettingsService;

    @BeforeEach
    void setUp() {
        settingsDocumentService = mock(SettingsDocumentService.class);
        serverRepository = mock(ServerCustomDomainRepository.class);
        cloudflareClient = mock(CloudflareClient.class);
        cloudflareConfiguration = mock(CloudflareConfiguration.class);
        customDomainAccessService = mock(CustomDomainAccessService.class);
        stateWriter = mock(CustomDomainStateWriter.class);
        when(cloudflareConfiguration.isConfigured()).thenReturn(true);
        domainSettingsService = new DomainSettingsService(
            settingsDocumentService,
            serverRepository,
            cloudflareClient,
            cloudflareConfiguration,
            customDomainAccessService,
            new CustomDomainStatusMapper(),
            stateWriter,
            new CustomDomainLockRegistry(),
            new ModlCorsProperties()
        );
    }

    @Test
    void configureDomainRejectsPlatformDomainBeforeCloudflare() {
        assertThrows(ValidationException.class, () -> domainSettingsService.configureDomain(server(), "panel.modl.gg"));

        verify(cloudflareClient, never()).createCustomHostname(any());
    }

    @Test
    void configureDomainRejectsIpLiteralBeforeCloudflare() {
        assertThrows(ValidationException.class, () -> domainSettingsService.configureDomain(server(), "127.0.0.1"));

        verify(cloudflareClient, never()).createCustomHostname(any());
    }

    @Test
    void configureDomainFailsWhenCloudflareUnconfigured() {
        when(cloudflareConfiguration.isConfigured()).thenReturn(false);

        assertThrows(ServiceUnavailableException.class,
            () -> domainSettingsService.configureDomain(server(), "example.org"));

        verify(cloudflareClient, never()).createCustomHostname(any());
    }

    @Test
    void configureDomainCanonicalizesAndPersistsToServer() {
        Server server = stubbedFreshServer();
        when(serverRepository.isCustomDomainOwnedByAnotherServer("example.org", "server-id")).thenReturn(false);
        when(cloudflareClient.findCustomHostnameByName("example.org")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("example.org"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("cf-id", "example.org", "pending", null, null, null));

        domainSettingsService.configureDomain(server, "Example.ORG");

        verify(cloudflareClient).createCustomHostname("example.org");
        verify(stateWriter).persist("server-id", "example.org", CustomDomainStatus.PENDING, "cf-id", null);
        verify(settingsDocumentService).deleteState(any(), eq(SETTINGS_TYPE));
    }

    @Test
    void configureDomainRejectsDomainOwnedByAnotherServer() {
        Server server = stubbedFreshServer();
        when(serverRepository.isCustomDomainOwnedByAnotherServer("example.org", "server-id")).thenReturn(true);

        assertThrows(ConflictException.class, () -> domainSettingsService.configureDomain(server, "example.org"));

        verify(cloudflareClient, never()).createCustomHostname(any());
    }

    @Test
    void configureDomainFailsLoudlyWhenCloudflareCreateFails() {
        Server server = stubbedFreshServer();
        when(serverRepository.isCustomDomainOwnedByAnotherServer("example.org", "server-id")).thenReturn(false);
        when(cloudflareClient.findCustomHostnameByName("example.org")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("example.org")).thenReturn(null);

        assertThrows(ExternalServiceException.class,
            () -> domainSettingsService.configureDomain(server, "example.org"));

        verify(stateWriter, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    void configureDomainDeletesOldHostnameOnChange() {
        Server server = stubbedFreshServer();
        server.setCustomDomainOverride("old.example.com");
        server.setCustomDomainCloudflareId("old-id");
        server.setCustomDomainStatus(CustomDomainStatus.ACTIVE);
        when(serverRepository.isCustomDomainOwnedByAnotherServer("new.example.com", "server-id")).thenReturn(false);
        when(cloudflareClient.findCustomHostnameByName("new.example.com")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("new.example.com"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("new-id", "new.example.com", "pending", null, null, null));
        when(cloudflareClient.deleteCustomHostname("old-id")).thenReturn(true);

        domainSettingsService.configureDomain(server, "new.example.com");

        verify(cloudflareClient).deleteCustomHostname("old-id");
        verify(cloudflareClient).createCustomHostname("new.example.com");
        verify(stateWriter).evict("old.example.com");
        verify(stateWriter).persist("server-id", "new.example.com", CustomDomainStatus.PENDING, "new-id", null);
    }

    @Test
    void configureDomainUsesFreshServerStateUnderLock() {
        Server stale = server();
        Server fresh = server();
        fresh.setCustomDomainOverride("old.example.com");
        fresh.setCustomDomainCloudflareId("old-id");
        when(serverRepository.findById("server-id")).thenReturn(Optional.of(fresh));
        when(serverRepository.isCustomDomainOwnedByAnotherServer("new.example.com", "server-id")).thenReturn(false);
        when(cloudflareClient.findCustomHostnameByName("new.example.com")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("new.example.com"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("new-id", "new.example.com", "pending", null, null, null));
        when(cloudflareClient.deleteCustomHostname("old-id")).thenReturn(true);

        domainSettingsService.configureDomain(stale, "new.example.com");

        verify(cloudflareClient).deleteCustomHostname("old-id");
        verify(stateWriter).evict("old.example.com");
    }

    @Test
    void configureDomainRollsBackCreatedHostnameOnDuplicateKey() {
        Server server = stubbedFreshServer();
        when(serverRepository.isCustomDomainOwnedByAnotherServer("example.org", "server-id")).thenReturn(false);
        when(cloudflareClient.findCustomHostnameByName("example.org")).thenReturn(null);
        when(cloudflareClient.createCustomHostname("example.org"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("new-id", "example.org", "pending", null, null, null));
        doThrow(new DuplicateKeyException("dup")).when(stateWriter)
            .persist("server-id", "example.org", CustomDomainStatus.PENDING, "new-id", null);

        assertThrows(ConflictException.class, () -> domainSettingsService.configureDomain(server, "example.org"));

        verify(cloudflareClient).deleteCustomHostname("new-id");
        verify(settingsDocumentService, never()).deleteState(any(), eq(SETTINGS_TYPE));
    }

    @Test
    void configureDomainIsNoOpWhenSameActiveDomainStillExistsInCloudflare() {
        Server server = stubbedFreshServer();
        server.setCustomDomainOverride("example.org");
        server.setCustomDomainCloudflareId("cf-id");
        server.setCustomDomainStatus(CustomDomainStatus.ACTIVE);
        when(serverRepository.isCustomDomainOwnedByAnotherServer("example.org", "server-id")).thenReturn(false);
        when(cloudflareClient.getCustomHostname("cf-id"))
            .thenReturn(new CloudflareClient.CustomHostnameResult("cf-id", "example.org", "active",
                new CloudflareClient.CustomHostnameResult.SslStatus("active", null, null), null, null));

        domainSettingsService.configureDomain(server, "example.org");

        verify(cloudflareClient, never()).createCustomHostname(any());
        verify(stateWriter, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    void getDomainSettingsReadsServerFields() {
        Server server = server();
        server.setCustomDomainOverride("custom.example.com");
        server.setCustomDomainStatus(CustomDomainStatus.ACTIVE);
        server.setCustomDomainLastChecked(new Date());
        when(customDomainAccessService.canManageCustomDomain(any())).thenReturn(true);

        DomainSettings result = domainSettingsService.getDomainSettings(server, "custom.example.com");

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
        when(customDomainAccessService.canManageCustomDomain(any())).thenReturn(false);

        DomainSettings result = domainSettingsService.getDomainSettings(server(), "custom.example.com");

        assertNull(result.getCustomDomain());
        assertNull(result.getStatus());
        assertFalse(result.isAccessingFromCustomDomain());
        assertEquals("https://tenant.modl.gg", result.getModlSubdomainUrl());
        assertFalse(result.isCanManageCustomDomain());
    }

    @Test
    void verifyUpdatesStatusFromCloudflare() {
        Server server = server();
        server.setCustomDomainOverride("example.org");
        server.setCustomDomainCloudflareId("cf-id");
        when(cloudflareClient.getCustomHostname("cf-id")).thenReturn(new CloudflareClient.CustomHostnameResult(
            "cf-id", "example.org", "active",
            new CloudflareClient.CustomHostnameResult.SslStatus("active", null, null), null, null));
        when(stateWriter.reconcileStatus("server-id", "example.org", CustomDomainStatus.ACTIVE, "cf-id", null))
            .thenReturn(true);

        DomainSettings result = domainSettingsService.verifyDomain(server, "example.org");

        assertEquals("active", result.getStatus().getStatus());
        verify(stateWriter).reconcileStatus("server-id", "example.org", CustomDomainStatus.ACTIVE, "cf-id", null);
    }

    @Test
    void verifyFailsWhenDomainWasConcurrentlyRemoved() {
        Server server = server();
        server.setCustomDomainOverride("example.org");
        server.setCustomDomainCloudflareId("cf-id");
        when(cloudflareClient.getCustomHostname("cf-id")).thenReturn(new CloudflareClient.CustomHostnameResult(
            "cf-id", "example.org", "active",
            new CloudflareClient.CustomHostnameResult.SslStatus("active", null, null), null, null));
        when(stateWriter.reconcileStatus("server-id", "example.org", CustomDomainStatus.ACTIVE, "cf-id", null))
            .thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
            () -> domainSettingsService.verifyDomain(server, "example.org"));
    }

    @Test
    void removeDeletesHostnameAndClearsServerFields() {
        Server server = server();
        server.setCustomDomainOverride("example.org");
        server.setCustomDomainCloudflareId("cf-id");
        when(cloudflareClient.deleteCustomHostname("cf-id")).thenReturn(true);

        domainSettingsService.removeDomain(server);

        verify(cloudflareClient).deleteCustomHostname("cf-id");
        verify(stateWriter).clear("server-id", "example.org");
        verify(settingsDocumentService).deleteState(any(), eq(SETTINGS_TYPE));
    }

    @Test
    void verifyRejectsMismatchedDomain() {
        Server server = server();
        server.setCustomDomainOverride("example.org");

        assertThrows(ValidationException.class, () -> domainSettingsService.verifyDomain(server, "other.example.com"));
        verify(stateWriter, never()).persist(any(), any(), any(), any(), isNull());
    }

    private Server stubbedFreshServer() {
        Server server = server();
        when(serverRepository.findById("server-id")).thenReturn(Optional.of(server));
        return server;
    }

    private static Server server() {
        Server server = new Server("server", "tenant", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-id");
        return server;
    }
}
