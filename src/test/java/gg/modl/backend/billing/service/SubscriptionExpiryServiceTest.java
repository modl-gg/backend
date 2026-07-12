package gg.modl.backend.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.ServerLookupRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import gg.modl.backend.server.service.ServerMutationHelper;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryServiceTest {

    @Mock
    private ServerLookupRepository serverRepository;

    @Mock
    private UsageTrackingService usageTrackingService;

    @Mock
    private ServerMutationHelper serverMutationHelper;

    private SubscriptionExpiryService subscriptionExpiryService;

    @BeforeEach
    void setUp() {
        subscriptionExpiryService = new SubscriptionExpiryService(serverRepository, usageTrackingService, serverMutationHelper);
    }

    @Test
    void checkExpiredSubscriptionsDowngradesExpiredCanceledServer() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-1");
        server.setSubscriptionStatus(SubscriptionStatus.CANCELED);
        server.setCurrentPeriodEnd(new Date(System.currentTimeMillis() - 1000));
        when(serverRepository.findCancelledWithPeriodEnd()).thenReturn(List.of(server));

        doAnswer(invocation -> {
            Consumer<Server> mutator = invocation.getArgument(1);
            mutator.accept(invocation.getArgument(0));
            return null;
        }).when(serverMutationHelper).mutate(any(Server.class), any());

        subscriptionExpiryService.checkExpiredSubscriptions();

        verify(serverMutationHelper).mutate(any(Server.class), any());
        assertEquals(SubscriptionStatus.INACTIVE, server.getSubscriptionStatus());
        assertEquals(ServerPlan.FREE, server.getPlan());
        assertNull(server.getCurrentPeriodEnd());
        verify(usageTrackingService).resetUsageCounters("server-1");
    }
}
