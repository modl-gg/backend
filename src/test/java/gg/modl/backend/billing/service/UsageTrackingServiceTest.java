package gg.modl.backend.billing.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerMutationHelper;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageTrackingServiceTest {

    @Mock
    private ServerMongoRepository serverRepository;

    @Mock
    private ServerMutationHelper serverMutationHelper;

    private UsageTrackingService usageTrackingService;

    @BeforeEach
    void setUp() {
        usageTrackingService = new UsageTrackingService(serverRepository, serverMutationHelper);
    }

    @Test
    void updateUsageBillingSettingsPersistsFlagsThroughRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-1");
        server.setStripeCustomerId("cus_123");

        doAnswer(invocation -> {
            Consumer<Server> mutator = invocation.getArgument(1);
            mutator.accept(invocation.getArgument(0));
            return null;
        }).when(serverMutationHelper).mutate(any(Server.class), any());

        usageTrackingService.updateUsageBillingSettings(server, true);

        verify(serverMutationHelper).mutate(any(Server.class), any());
        assertTrue(Boolean.TRUE.equals(server.getUsageBillingEnabled()));
        assertTrue(server.getUsageBillingUpdatedAt() != null);
    }

    @Test
    void incrementCdnUsageUsesTypedAtomicUpdate() {
        usageTrackingService.incrementCdnUsage("server-1", 1.5);

        verify(serverRepository).incrementCdnUsage("server-1", 1.5);
    }
}
