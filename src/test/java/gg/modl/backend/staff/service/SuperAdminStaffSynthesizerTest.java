package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.staff.data.Staff;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuperAdminStaffSynthesizerTest {

    private static final String ADMIN_EMAIL = "owner@example.com";
    private static final Date SERVER_CREATED_AT = new Date(1_700_000_000_000L);

    private Server server;

    @BeforeEach
    void setUp() {
        server = new Server("Server", "server", "server_db", ADMIN_EMAIL, true, ServerPlan.FREE);
        server.setId("server-id");
        server.setCreatedAt(SERVER_CREATED_AT);
    }

    @Test
    void synthesizeForProducesTheCanonicalSuperAdminProfile() {
        Staff synthesized = SuperAdminStaffSynthesizer.synthesizeFor(server, ADMIN_EMAIL);

        assertEquals(ADMIN_EMAIL, synthesized.getEmail());
        assertEquals("Admin", synthesized.getUsername());
        assertEquals(SuperAdminStaffSynthesizer.SUPER_ADMIN_USERNAME, synthesized.getUsername());
        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_ID, synthesized.getRoleId());
        assertEquals(SERVER_CREATED_AT, synthesized.getCreatedAt());
        assertNotNull(synthesized.getUpdatedAt());
        assertNull(synthesized.getId());
    }

    @Test
    void synthesizeIfAdminEmailMatchesCaseInsensitivelyAndReturnsIdenticalProfile() {
        Staff synthesized = SuperAdminStaffSynthesizer.synthesizeIfAdminEmail(server, "OWNER@example.COM");

        assertNotNull(synthesized);
        assertEquals(ADMIN_EMAIL, synthesized.getEmail());
        assertEquals("Admin", synthesized.getUsername());
        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_ID, synthesized.getRoleId());
        assertEquals(SERVER_CREATED_AT, synthesized.getCreatedAt());
    }

    @Test
    void synthesizeIfAdminEmailReturnsNullForNonAdminEmail() {
        assertNull(SuperAdminStaffSynthesizer.synthesizeIfAdminEmail(server, "someone@example.com"));
    }

    @Test
    void synthesizeIfAdminEmailReturnsNullWhenServerHasNoAdminEmail() {
        Server serverWithoutAdmin = mock(Server.class);
        when(serverWithoutAdmin.getAdminEmail()).thenReturn(null);

        assertNull(SuperAdminStaffSynthesizer.synthesizeIfAdminEmail(serverWithoutAdmin, ADMIN_EMAIL));
    }
}
