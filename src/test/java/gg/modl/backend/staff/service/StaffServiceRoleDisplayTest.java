package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.StaffResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StaffServiceRoleDisplayTest {

    private static final String ADMIN_EMAIL = "owner@example.com";
    private static final String STALE_EMAIL = "stale@example.com";

    @Test
    void staleSuperAdminRoleStillRendersItsStoredRoleName() {
        StaffMongoRepository staffRepository = mock(StaffMongoRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        InvitationMongoRepository invitationRepository = mock(InvitationMongoRepository.class);
        Server server = server();

        when(staffRepository.findAll(server)).thenReturn(List.of(staleSuperAdminStaff()));
        when(invitationRepository.findActiveInvitations(eq(server), any(Date.class))).thenReturn(List.of());
        when(permissionService.resolveRoleNames(eq(server), any()))
            .thenReturn(Map.of(RoleAuthorization.SUPER_ADMIN_ROLE_ID, RoleAuthorization.SUPER_ADMIN_ROLE_NAME));

        List<StaffResponse> staff = staffService(invitationRepository, staffRepository, permissionService).getAllStaff(server);

        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_NAME,
            staff.stream().filter(entry -> STALE_EMAIL.equals(entry.email())).findFirst().orElseThrow().role());
    }

    @Test
    void staleSuperAdminRoleConfersNoEffectiveRoleName() {
        StaffRoleMongoRepository roleRepository = mock(StaffRoleMongoRepository.class);
        PermissionService permissionService =
            new PermissionService(roleRepository, mock(PunishmentTypeService.class), mock(StaffMongoRepository.class));
        Server server = server();
        when(roleRepository.findById(server, RoleAuthorization.SUPER_ADMIN_ROLE_ID)).thenReturn(Optional.of(StaffRole.builder()
            .id(RoleAuthorization.SUPER_ADMIN_ROLE_ID)
            .name(RoleAuthorization.SUPER_ADMIN_ROLE_NAME)
            .build()));

        Staff stale = staleSuperAdminStaff();

        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_NAME, permissionService.assignedRoleName(server, stale));
        assertEquals("", permissionService.effectiveRoleName(server, stale));
    }

    private StaffService staffService(InvitationMongoRepository invitationRepository,
                                      StaffMongoRepository staffRepository,
                                      PermissionService permissionService) {
        return new StaffService(
            invitationRepository,
            staffRepository,
            permissionService,
            mock(RoleAuthorization.class),
            mock(ServerTimestampService.class),
            mock(WebAuthnService.class),
            mock(SessionService.class),
            mock(GeneralSettingsService.class),
            mock(StaffLookupCache.class)
        );
    }

    private Staff staleSuperAdminStaff() {
        return Staff.builder()
            .id("stale-staff-id")
            .email(STALE_EMAIL)
            .username("stale")
            .roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID)
            .build();
    }

    private Server server() {
        Server server = new Server("Server", "server", "server_db", ADMIN_EMAIL, true, ServerPlan.FREE);
        server.setId("server-id");
        return server;
    }
}
