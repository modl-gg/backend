package gg.modl.backend.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ProvisioningException;
import gg.modl.backend.server.service.ServerProvisioningService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerServiceTest {
    @Test
    void verifyEmailTokenMarksCompletedWhenProvisioningSucceeds() {
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        ServerProvisioningService provisioningService = mock(ServerProvisioningService.class);
        ModlCorsProperties corsProperties = new ModlCorsProperties();
        ServerService serverService = new ServerService(serverRepository, provisioningService, corsProperties);

        Server claimed = mock(Server.class);
        when(claimed.getId()).thenReturn("server-1");
        when(serverRepository.verifyEmailTokenAtomically("token")).thenReturn(Optional.of(claimed));
        doNothing().when(provisioningService).provision(claimed);
        when(serverRepository.markProvisioningCompleted("server-1")).thenReturn(true);

        Server result = serverService.verifyEmailToken("token");

        assertThat(result).isSameAs(claimed);
        verify(serverRepository).verifyEmailTokenAtomically("token");
        verify(provisioningService).provision(claimed);
        verify(serverRepository).markProvisioningCompleted("server-1");
        verify(serverRepository, never()).markProvisioningFailed(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
        verify(claimed).setProvisioningStatus(ProvisioningStatus.COMPLETED);
    }

    @Test
    void verifyEmailTokenMarksFailedWhenProvisioningThrows() {
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        ServerProvisioningService provisioningService = mock(ServerProvisioningService.class);
        ModlCorsProperties corsProperties = new ModlCorsProperties();
        ServerService serverService = new ServerService(serverRepository, provisioningService, corsProperties);

        Server claimed = mock(Server.class);
        when(claimed.getId()).thenReturn("server-1");
        when(serverRepository.verifyEmailTokenAtomically("token")).thenReturn(Optional.of(claimed));
        doThrow(new ProvisioningException("boom", new RuntimeException("boom")))
            .when(provisioningService).provision(claimed);

        Server result = serverService.verifyEmailToken("token");

        assertThat(result).isSameAs(claimed);
        verify(provisioningService).provision(claimed);
        verify(serverRepository).markProvisioningFailed(org.mockito.ArgumentMatchers.eq("server-1"),
            org.mockito.ArgumentMatchers.anyString());
        verify(serverRepository, never()).markProvisioningCompleted(org.mockito.ArgumentMatchers.anyString());
        verify(claimed).setProvisioningStatus(ProvisioningStatus.FAILED);
    }

    @Test
    void verifyEmailTokenReturnsNullForInvalidToken() {
        ServerMongoRepository serverRepository = mock(ServerMongoRepository.class);
        ServerProvisioningService provisioningService = mock(ServerProvisioningService.class);
        ModlCorsProperties corsProperties = new ModlCorsProperties();
        ServerService serverService = new ServerService(serverRepository, provisioningService, corsProperties);

        when(serverRepository.verifyEmailTokenAtomically("bogus")).thenReturn(Optional.empty());

        Server result = serverService.verifyEmailToken("bogus");

        assertThat(result).isNull();
        verify(provisioningService, never()).provision(org.mockito.ArgumentMatchers.any());
    }
}
