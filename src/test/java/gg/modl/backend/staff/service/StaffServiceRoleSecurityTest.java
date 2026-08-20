package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffServiceRoleSecurityTest {

    private final StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final RoleAuthorization roleAuthorization = new RoleAuthorization(permissionService, staffRepository, mock(StaffLookupCache.class));
    private final StaffService service = new StaffService(
        mock(InvitationMongoRepository.class),
        staffRepository,
        permissionService,
        roleAuthorization,
        mock(ServerTimestampService.class),
        mock(WebAuthnService.class),
        mock(SessionService.class),
        mock(GeneralSettingsService.class),
        mock(StaffLookupCache.class)
    );

    @Test
    void memberManagerCannotAssignRoleAboveOwnAuthority() {
        Server server = server();
        Staff target = staff("helper-staff-id", "target@example.com", "helper");
        when(staffRepository.findById(server, "helper-staff-id")).thenReturn(Optional.of(target));
        when(staffRepository.findByEmailIgnoreCase(server, "mod@example.com"))
            .thenReturn(Optional.of(staff("mod-staff-id", "mod@example.com", "moderator")));
        when(permissionService.getRoleById(server, "moderator")).thenReturn(Optional.of(role("moderator", "Moderator", 2)));
        when(permissionService.getRoleById(server, "helper")).thenReturn(Optional.of(role("helper", "Helper", 3)));
        when(permissionService.getRoleByName(server, "Admin")).thenReturn(Optional.of(role("admin", "Admin", 1)));

        RoleAuthorization.PerformerAuthority performer = roleAuthorization.panelPerformer(server, "mod@example.com");

        assertThrows(
            ForbiddenException.class,
            () -> service.updateStaffRole(server, "helper-staff-id", "Admin", performer)
        );
        verify(staffRepository, never()).saveEntity(any(Server.class), any(Staff.class));
    }

    @Test
    void subordinateCannotDemoteSuperior() {
        Server server = server();
        Staff superior = staff("admin-staff-id", "admin@example.com", "admin");
        when(staffRepository.findById(server, "admin-staff-id")).thenReturn(Optional.of(superior));
        when(staffRepository.findByEmailIgnoreCase(server, "mod@example.com"))
            .thenReturn(Optional.of(staff("mod-staff-id", "mod@example.com", "moderator")));
        when(permissionService.getRoleById(server, "moderator")).thenReturn(Optional.of(role("moderator", "Moderator", 2)));
        when(permissionService.getRoleById(server, "admin")).thenReturn(Optional.of(role("admin", "Admin", 1)));

        RoleAuthorization.PerformerAuthority performer = roleAuthorization.panelPerformer(server, "mod@example.com");

        assertThrows(
            ForbiddenException.class,
            () -> service.updateStaffRole(server, "admin-staff-id", "Helper", performer)
        );
        verify(staffRepository, never()).saveEntity(any(Server.class), any(Staff.class));
    }

    @Test
    void subordinateCannotDeleteSuperior() {
        Server server = server();
        Staff superior = staff("admin-staff-id", "admin@example.com", "admin");
        when(staffRepository.findById(server, "admin-staff-id")).thenReturn(Optional.of(superior));
        when(staffRepository.findByEmailIgnoreCase(server, "mod@example.com"))
            .thenReturn(Optional.of(staff("mod-staff-id", "mod@example.com", "moderator")));
        when(permissionService.getRoleById(server, "moderator")).thenReturn(Optional.of(role("moderator", "Moderator", 2)));
        when(permissionService.getRoleById(server, "admin")).thenReturn(Optional.of(role("admin", "Admin", 1)));

        RoleAuthorization.PerformerAuthority performer = roleAuthorization.panelPerformer(server, "mod@example.com");

        assertThrows(
            ForbiddenException.class,
            () -> service.deleteStaff(server, "admin-staff-id", performer)
        );
        verify(staffRepository, never()).deleteById(server, "admin-staff-id");
    }

    private Staff staff(String id, String email, String roleId) {
        return Staff.builder()
            .id(id)
            .email(email)
            .username(id)
            .roleId(roleId)
            .build();
    }

    private StaffRole role(String id, String name, int order) {
        return StaffRole.builder()
            .id(id)
            .name(name)
            .order(order)
            .permissions(List.of("admin.staff.manage.members"))
            .build();
    }

    private Server server() {
        Server server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
