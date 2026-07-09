package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffServiceStaffCountTest {

    private static final String ADMIN_EMAIL = "owner@example.com";

    private StaffMongoRepository staffRepository;
    private StaffService service;
    private Server server;

    @BeforeEach
    void setUp() {
        staffRepository = mock(StaffMongoRepository.class);
        service = new StaffService(
            mock(InvitationMongoRepository.class),
            staffRepository,
            mock(PlayerMongoRepository.class),
            mock(PunishmentMongoRepository.class),
            mock(PlayerService.class),
            mock(PermissionService.class),
            mock(RoleAuthorization.class),
            mock(ServerTimestampService.class),
            mock(WebAuthnService.class),
            mock(SessionService.class),
            mock(GeneralSettingsService.class)
        );
        server = new Server("Server", "server", "server_db", ADMIN_EMAIL, true, ServerPlan.FREE);
        server.setId("server-id");
        server.setCreatedAt(new Date(1_700_000_000_000L));
    }

    @Test
    void countsSuperAdminWithoutStaffRecord() {
        when(staffRepository.countAll(server)).thenReturn(0L);
        when(staffRepository.existsByEmailEqualsIgnoreCase(server, ADMIN_EMAIL)).thenReturn(false);

        assertEquals(1L, service.countStaffIncludingSuperAdmin(server));
    }

    @Test
    void doesNotDoubleCountSuperAdminWithStaffRecord() {
        when(staffRepository.countAll(server)).thenReturn(3L);
        when(staffRepository.existsByEmailEqualsIgnoreCase(server, ADMIN_EMAIL)).thenReturn(true);

        assertEquals(3L, service.countStaffIncludingSuperAdmin(server));
    }

    @Test
    void countsOnlyStaffRecordsWhenAdminEmailMissing() {
        Server serverWithoutAdminEmail = mock(Server.class);
        when(serverWithoutAdminEmail.getAdminEmail()).thenReturn(null);
        when(staffRepository.countAll(serverWithoutAdminEmail)).thenReturn(2L);

        assertEquals(2L, service.countStaffIncludingSuperAdmin(serverWithoutAdminEmail));
        verify(staffRepository, never()).existsByEmailEqualsIgnoreCase(any(), any());
    }
}
