package gg.modl.backend.billing.service;

import gg.modl.backend.database.mongo.repository.ServerMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageTrackingServiceTest {

    @Mock
    private ServerMongoRepository serverRepository;

    private UsageTrackingService usageTrackingService;

    @BeforeEach
    void setUp() {
        usageTrackingService = new UsageTrackingService(serverRepository);
    }

    @Test
    void updateUsageBillingSettingsPersistsFlagsThroughRepository() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.PREMIUM);
        server.setId("server-1");
        server.setStripeCustomerId("cus_123");

        usageTrackingService.updateUsageBillingSettings(server, true);

        ArgumentCaptor<Server> updatedServerCaptor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).saveEntity(updatedServerCaptor.capture());
        assertTrue(Boolean.TRUE.equals(updatedServerCaptor.getValue().getUsageBillingEnabled()));
        assertTrue(updatedServerCaptor.getValue().getUsageBillingUpdatedAt() != null);
    }

    @Test
    void incrementCdnUsageUsesTypedAtomicUpdate() {
        usageTrackingService.incrementCdnUsage("server-1", 1.5);

        verify(serverRepository).incrementCdnUsage("server-1", 1.5);
    }
}
