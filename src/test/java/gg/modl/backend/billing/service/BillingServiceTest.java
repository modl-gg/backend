package gg.modl.backend.billing.service;

import com.stripe.model.checkout.Session;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private StripeService stripeService;

    @Mock
    private ServerMongoRepository serverRepository;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(stripeService, serverRepository);
    }

    @Test
    void createCheckoutSessionPersistsStripeCustomerIdThroughRepository() throws Exception {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-1");

        Session session = mock(Session.class);
        when(stripeService.createCustomer(server)).thenReturn("cus_123");
        when(stripeService.createCheckoutSession("cus_123", server.getCustomDomain())).thenReturn(session);
        when(session.getId()).thenReturn("sess_123");
        when(session.getUrl()).thenReturn("https://checkout.example.com");

        billingService.createCheckoutSession(server);

        ArgumentCaptor<Server> updatedServerCaptor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).saveEntity(updatedServerCaptor.capture());
        assertEquals("cus_123", updatedServerCaptor.getValue().getStripeCustomerId());
    }
}
