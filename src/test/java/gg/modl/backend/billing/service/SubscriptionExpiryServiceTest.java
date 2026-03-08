package gg.modl.backend.billing.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryServiceTest {

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private UsageTrackingService usageTrackingService;

    private SubscriptionExpiryService subscriptionExpiryService;

    @BeforeEach
    void setUp() {
        subscriptionExpiryService = new SubscriptionExpiryService(serverRepository, usageTrackingService);
    }

    @Test
    void checkExpiredSubscriptionsDowngradesExpiredCanceledServer() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-1");
        server.setSubscriptionStatus(SubscriptionStatus.CANCELED);
        server.setCurrentPeriodEnd(new Date(System.currentTimeMillis() - 1000));
        when(serverRepository.findCancelledWithPeriodEnd()).thenReturn(List.of(server));

        subscriptionExpiryService.checkExpiredSubscriptions();

        ArgumentCaptor<Server> updatedServerCaptor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).saveEntity(updatedServerCaptor.capture());
        assertEquals(SubscriptionStatus.INACTIVE, updatedServerCaptor.getValue().getSubscriptionStatus());
        assertEquals(ServerPlan.FREE, updatedServerCaptor.getValue().getPlan());
        assertNull(updatedServerCaptor.getValue().getCurrentPeriodEnd());
        verify(usageTrackingService).resetUsageCounters("server-1");
    }
}
