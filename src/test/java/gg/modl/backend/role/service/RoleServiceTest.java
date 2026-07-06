package gg.modl.backend.role.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleServiceTest {

    private static final RoleAuthorization.PerformerAuthority SUPER_ADMIN =
        new RoleAuthorization.PerformerAuthority(null, true, true);

    @Test
    void defaultTicketRolesIncludeAppealModifyPermission() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RoleService roleService = new RoleService(
            roleRepository,
            staffRepository,
            permissionService,
            new RoleAuthorization(permissionService, staffRepository),
            mock(ServerTimestampService.class)
        );
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        when(permissionService.getPunishmentPermissions(server)).thenReturn(List.of());
        when(permissionService.getAllPermissionIds(server)).thenReturn(List.of(
            "ticket.view.all",
            "ticket.reply.all",
            "appeal.modify"
        ));

        roleService.createDefaultRoles(server);

        ArgumentCaptor<StaffRole> captor = ArgumentCaptor.forClass(StaffRole.class);
        verify(roleRepository, org.mockito.Mockito.times(4)).upsertRole(org.mockito.Mockito.eq(server), captor.capture());
        for (StaffRole role : captor.getAllValues()) {
            if (!"super-admin".equals(role.getId())) {
                assertTrue(role.getPermissions().contains("appeal.modify"), role.getId());
            }
        }
    }

    @Test
    void updateRolePermissionsRejectsSuperAdminRole() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RoleService roleService = roleService(roleRepository, permissionService);
        Server server = server();

        assertThrows(ForbiddenException.class,
            () -> roleService.updateRolePermissions(server, "super-admin", List.of("ticket.reply.all"), SUPER_ADMIN));
        assertThrows(ForbiddenException.class,
            () -> roleService.updateRolePermissions(server, "custom-super-admin-x", List.of("ticket.reply.all"), SUPER_ADMIN));
        verify(roleRepository, never()).saveEntity(any(Server.class), any());
    }

    @Test
    void updateRolePermissionsReturnsFalseForMissingRole() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RoleService roleService = roleService(roleRepository, permissionService);
        Server server = server();
        when(roleRepository.findById(server, "missing")).thenReturn(Optional.empty());

        assertFalse(roleService.updateRolePermissions(server, "missing", List.of("ticket.reply.all"), SUPER_ADMIN));
        verify(roleRepository, never()).saveEntity(any(Server.class), any());
    }

    @Test
    void updateRolePermissionsFiltersInvalidPermissionIds() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RoleService roleService = roleService(roleRepository, permissionService);
        Server server = server();
        StaffRole role = StaffRole.builder()
            .id("custom-1").name("Custom").order(5)
            .permissions(new ArrayList<>(List.of("ticket.reply.all", "ticket.close.all")))
            .build();
        when(roleRepository.findById(server, "custom-1")).thenReturn(Optional.of(role));
        when(permissionService.getAllPermissionIds(server)).thenReturn(List.of("ticket.reply.all", "ticket.close.all"));
        when(roleRepository.saveEntity(eq(server), any())).thenAnswer(inv -> inv.getArgument(1));

        boolean result = roleService.updateRolePermissions(
            server, "custom-1", List.of("ticket.reply.all", "bogus.perm"), SUPER_ADMIN);

        assertTrue(result);
        ArgumentCaptor<StaffRole> captor = ArgumentCaptor.forClass(StaffRole.class);
        verify(roleRepository).saveEntity(eq(server), captor.capture());
        assertEquals(List.of("ticket.reply.all"), captor.getValue().getPermissions());
    }

    @Test
    void updateRolePermissionsRejectsExpansionWithPerformerIdentity() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RoleService roleService = roleService(roleRepository, permissionService);
        Server server = server();
        StaffRole targetRole = StaffRole.builder()
            .id("custom-target").name("Target").order(5)
            .permissions(new ArrayList<>(List.of("ticket.reply.all")))
            .build();
        StaffRole performerRole = StaffRole.builder()
            .id("custom-performer").name("Performer").order(2)
            .permissions(new ArrayList<>(List.of("ticket.reply.all")))
            .build();
        when(roleRepository.findById(server, "custom-target")).thenReturn(Optional.of(targetRole));
        when(permissionService.getAllPermissionIds(server)).thenReturn(List.of("ticket.reply.all", "punishment.modify"));
        when(permissionService.getRoleById(server, "custom-performer")).thenReturn(Optional.of(performerRole));
        when(permissionService.hasPermission(server, "custom-performer", RoleAuthorization.MANAGE_ROLES_PERMISSION))
            .thenReturn(true);
        when(roleRepository.saveEntity(eq(server), any())).thenAnswer(inv -> inv.getArgument(1));

        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("custom-performer", false, true);
        boolean result = roleService.updateRolePermissions(
            server, "custom-target", List.of("ticket.reply.all", "punishment.modify"), performer);

        assertTrue(result);
        ArgumentCaptor<StaffRole> captor = ArgumentCaptor.forClass(StaffRole.class);
        verify(roleRepository).saveEntity(eq(server), captor.capture());
        assertEquals(List.of("ticket.reply.all"), captor.getValue().getPermissions());
    }

    private RoleService roleService(StaffRoleMongoRepository roleRepository, PermissionService permissionService) {
        StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
        return new RoleService(
            roleRepository,
            staffRepository,
            permissionService,
            new RoleAuthorization(permissionService, staffRepository),
            mock(ServerTimestampService.class));
    }

    private Server server() {
        Server server = new Server("server", "domain", "db", "admin@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
