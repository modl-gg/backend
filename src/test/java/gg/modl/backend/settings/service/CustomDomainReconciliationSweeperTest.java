package gg.modl.backend.settings.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.cloudflare.config.CloudflareConfiguration;
import gg.modl.backend.cloudflare.external.CloudflareClient;
import gg.modl.backend.database.mongo.repository.ServerCustomDomainRepository;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.server.data.CustomDomainStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.config.CustomDomainReconciliationProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomDomainReconciliationSweeperTest {
    private CustomDomainReconciliationProperties properties;
    private CloudflareConfiguration cloudflareConfiguration;
    private CloudflareClient cloudflareClient;
    private ServerCustomDomainRepository serverRepository;
    private CustomDomainStateWriter stateWriter;
    private CustomDomainReconciliationSweeper sweeper;

    @BeforeEach
    void setUp() {
        properties = new CustomDomainReconciliationProperties();
        cloudflareConfiguration = mock(CloudflareConfiguration.class);
        cloudflareClient = mock(CloudflareClient.class);
        serverRepository = mock(ServerCustomDomainRepository.class);
        stateWriter = mock(CustomDomainStateWriter.class);
        when(cloudflareConfiguration.isConfigured()).thenReturn(true);
        sweeper = new CustomDomainReconciliationSweeper(
            properties,
            cloudflareConfiguration,
            cloudflareClient,
            serverRepository,
            new CustomDomainStatusMapper(),
            stateWriter,
            new CustomDomainLockRegistry()
        );
    }

    @Test
    void disabledSweeperDoesNothing() {
        properties.setEnabled(false);

        sweeper.runReconciliationOnce();

        verify(serverRepository, never()).findAllWithCustomDomainOverride();
        verify(cloudflareClient, never()).listAllCustomHostnames();
    }

    @Test
    void lookupFailureSkipsServerWithoutMutationAndContinuesSweep() {
        Server failing = server("server-1", "a.example.com", "cf-1", CustomDomainStatus.ACTIVE);
        Server healthy = server("server-2", "b.example.com", "cf-2", CustomDomainStatus.PENDING);
        when(serverRepository.findAllWithCustomDomainOverride()).thenReturn(List.of(failing, healthy));
        when(serverRepository.findById("server-1")).thenReturn(Optional.of(failing));
        when(serverRepository.findById("server-2")).thenReturn(Optional.of(healthy));
        when(cloudflareClient.getCustomHostname("cf-1")).thenThrow(new ExternalServiceException("cf down"));
        when(cloudflareClient.getCustomHostname("cf-2")).thenReturn(activeHostname("cf-2", "b.example.com"));
        when(stateWriter.reconcileStatus("server-2", "b.example.com", CustomDomainStatus.ACTIVE, "cf-2", null))
            .thenReturn(true);
        properties.setOrphanGarbageCollection(false);

        sweeper.runReconciliationOnce();

        verify(stateWriter, never()).reconcileStatus(eq("server-1"), any(), any(), any(), any());
        verify(stateWriter, never()).evict("a.example.com");
        verify(stateWriter).reconcileStatus("server-2", "b.example.com", CustomDomainStatus.ACTIVE, "cf-2", null);
    }

    @Test
    void gcSkippedWhenPropertyDisabled() {
        properties.setOrphanGarbageCollection(false);
        when(serverRepository.findAllWithCustomDomainOverride()).thenReturn(List.of());

        sweeper.runReconciliationOnce();

        verify(cloudflareClient, never()).listAllCustomHostnames();
    }

    @Test
    void gcSkipsClaimedDomainsAndDeletesUnclaimedAfterRecheck() {
        Server claimed = server("server-1", "claimed.example.com", "cf-1", CustomDomainStatus.ACTIVE);
        when(serverRepository.findAllWithCustomDomainOverride()).thenReturn(List.of(claimed));
        when(serverRepository.findById("server-1")).thenReturn(Optional.of(claimed));
        when(cloudflareClient.getCustomHostname("cf-1")).thenReturn(activeHostname("cf-1", "claimed.example.com"));
        when(cloudflareClient.listAllCustomHostnames()).thenReturn(List.of(
            activeHostname("cf-1", "claimed.example.com"),
            activeHostname("orphan-id", "Orphan.example.com")
        ));
        when(serverRepository.isCustomDomainClaimed("orphan.example.com")).thenReturn(false);
        when(cloudflareClient.deleteCustomHostname("orphan-id")).thenReturn(true);

        sweeper.runReconciliationOnce();

        verify(serverRepository).isCustomDomainClaimed("orphan.example.com");
        verify(serverRepository, never()).isCustomDomainClaimed("claimed.example.com");
        verify(cloudflareClient).deleteCustomHostname("orphan-id");
        verify(cloudflareClient, never()).deleteCustomHostname("cf-1");
    }

    @Test
    void gcRecheckUnderLockPreventsDeletion() {
        when(serverRepository.findAllWithCustomDomainOverride()).thenReturn(List.of());
        when(cloudflareClient.listAllCustomHostnames()).thenReturn(List.of(
            activeHostname("orphan-id", "orphan.example.com")
        ));
        when(serverRepository.isCustomDomainClaimed("orphan.example.com")).thenReturn(true);

        sweeper.runReconciliationOnce();

        verify(cloudflareClient, never()).deleteCustomHostname(anyString());
    }

    private CloudflareClient.CustomHostnameResult activeHostname(String id, String hostname) {
        return new CloudflareClient.CustomHostnameResult(id, hostname, "active",
            new CloudflareClient.CustomHostnameResult.SslStatus("active", "http", "dv"), null, null);
    }

    private Server server(String id, String domain, String cloudflareId, CustomDomainStatus status) {
        Server server = new Server(id, id, "db_" + id, "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId(id);
        server.setCustomDomainOverride(domain);
        server.setCustomDomainCloudflareId(cloudflareId);
        server.setCustomDomainStatus(status);
        return server;
    }
}
