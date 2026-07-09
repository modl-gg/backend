package gg.modl.backend.role.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    @Test
    void hasPermissionResolvesRoleByIdNotName() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService service = newService(roleRepository);
        Server server = server();
        StaffRole role = role("custom-1", "Support", 5, List.of("ticket.view.all"));
        when(roleRepository.findById(server, "custom-1")).thenReturn(Optional.of(role));

        assertTrue(service.hasPermission(server, "custom-1", "ticket.view.all"));
        assertFalse(service.hasPermission(server, "custom-1", "admin.staff.manage"));
        verify(roleRepository, never()).findByName(any(), any());
    }

    @Test
    void hasPermissionSurvivesRoleRename() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService service = newService(roleRepository);
        Server server = server();
        StaffRole renamed = role("custom-1", "Helpers", 5, List.of("ticket.view.all"));
        when(roleRepository.findById(server, "custom-1")).thenReturn(Optional.of(renamed));

        assertTrue(service.hasPermission(server, "custom-1", "ticket.view.all"));
    }

    @Test
    void hierarchicalPermissionStillMatches() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService service = newService(roleRepository);
        Server server = server();
        when(roleRepository.findById(server, "admin"))
            .thenReturn(Optional.of(role("admin", "Admin", 1, List.of("admin.staff.manage"))));

        assertTrue(service.hasPermission(server, "admin", "admin.staff.manage.members"));
    }

    @Test
    void resolveRoleNameReturnsCurrentNameAndFallsBackToId() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService service = newService(roleRepository);
        Server server = server();
        when(roleRepository.findById(server, "custom-1")).thenReturn(Optional.of(role("custom-1", "Support", 5, List.of())));
        when(roleRepository.findById(server, "ghost")).thenReturn(Optional.empty());

        assertEquals("Support", service.resolveRoleName(server, "custom-1"));
        assertEquals("ghost", service.resolveRoleName(server, "ghost"));
        assertEquals("", service.resolveRoleName(server, null));
    }

    @Test
    void resolveRoleNamesBuildsIdToNameMap() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService service = newService(roleRepository);
        Server server = server();
        when(roleRepository.findByIds(server, java.util.Set.of("admin", "helper")))
            .thenReturn(List.of(role("admin", "Admin", 1, List.of()), role("helper", "Helper", 3, List.of())));

        Map<String, String> names = service.resolveRoleNames(server, List.of("admin", "helper", "admin"));
        assertEquals("Admin", names.get("admin"));
        assertEquals("Helper", names.get("helper"));
    }

    private PermissionService newService(StaffRoleMongoRepository roleRepository) {
        return new PermissionService(roleRepository, mock(PunishmentTypeService.class), mock(StaffMongoRepository.class));
    }

    private StaffRole role(String id, String name, int order, List<String> permissions) {
        return StaffRole.builder().id(id).name(name).order(order).permissions(permissions).build();
    }

    private Server server() {
        Server server = new Server("Server", "server", "server_db", "owner@example.com", true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
