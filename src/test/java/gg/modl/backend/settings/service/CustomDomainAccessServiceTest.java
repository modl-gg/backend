package gg.modl.backend.settings.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDomainAccessServiceTest {

    private final CustomDomainAccessService service = new CustomDomainAccessService();

    @Test
    void freeServerWithoutGrandfatherFlagCannotManageCustomDomain() {
        Server server = new Server("test-server", "test", "server_test", "admin@example.com", false, ServerPlan.FREE);

        assertFalse(service.canManageCustomDomain(server));
    }

    @Test
    void freeServerWithGrandfatherFlagCanManageCustomDomain() {
        Server server = new Server("test-server", "test", "server_test", "admin@example.com", false, ServerPlan.FREE);
        server.setCustomDomainGrandfathered(true);

        assertTrue(service.canManageCustomDomain(server));
    }

    @Test
    void premiumServerCanManageCustomDomainWithoutGrandfatherFlag() {
        Server server = new Server("test-server", "test", "server_test", "admin@example.com", false, ServerPlan.PREMIUM);

        assertTrue(service.canManageCustomDomain(server));
    }
}
