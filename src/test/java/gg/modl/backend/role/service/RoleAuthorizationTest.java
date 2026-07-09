package gg.modl.backend.role.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.staff.data.Staff;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAuthorizationTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String NO_AUTHORITY_MESSAGE = "You do not have authority to perform this action";
    private static final String HIGHER_AUTHORITY_MESSAGE =
        "You do not have authority over a role at or above your own level";

    private StaffRoleMongoRepository roleRepository;
    private StaffMongoRepository staffRepository;
    private PunishmentTypeService punishmentTypeService;
    private ServerTimestampService serverTimestampService;
    private PermissionService permissionService;
    private RoleAuthorization roleAuthorization;
    private Server server;

    @BeforeEach
    void setUp() {
        roleRepository = mock(StaffRoleMongoRepository.class);
        staffRepository = mock(StaffMongoRepository.class);
        punishmentTypeService = mock(PunishmentTypeService.class);
        serverTimestampService = mock(ServerTimestampService.class);
        permissionService = new PermissionService(roleRepository, punishmentTypeService, staffRepository);
        roleAuthorization = new RoleAuthorization(permissionService, staffRepository);
        server = new Server("server", "domain", "db", ADMIN_EMAIL, true, ServerPlan.FREE);
        server.setId("server-id");
        when(punishmentTypeService.getPunishmentTypes(server)).thenReturn(List.of());
    }

    @Test
    void minecraftPerformerMapsResolvedStaffRoleIdAndIdentity() {
        givenStaff("staff-mgr", "mgr@example.com", "mgr");

        RoleAuthorization.PerformerAuthority performer = roleAuthorization.minecraftPerformer(server, "staff-mgr");

        assertTrue(performer.identified());
        assertFalse(performer.superAdmin());
        assertEquals("mgr", performer.roleId());
    }

    @Test
    void minecraftPerformerFlagsSuperAdminByEmail() {
        givenStaff("staff-admin", ADMIN_EMAIL, null);

        RoleAuthorization.PerformerAuthority performer = roleAuthorization.minecraftPerformer(server, "staff-admin");

        assertTrue(performer.superAdmin());
        assertTrue(performer.identified());
    }

    @Test
    void minecraftPerformerIsUnidentifiedForMissingOrBlankActor() {
        when(staffRepository.findById(server, "ghost")).thenReturn(Optional.empty());

        assertFalse(roleAuthorization.minecraftPerformer(server, null).identified());
        assertFalse(roleAuthorization.minecraftPerformer(server, "  ").identified());
        assertFalse(roleAuthorization.minecraftPerformer(server, "ghost").identified());
    }

    @Test
    void effectiveRoleIdNullsSuperAdminRoleForNonAdminEmail() {
        Staff stale = Staff.builder().email("old-owner@example.com").roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID).build();

        assertNull(RoleAuthorization.effectiveRoleId(server, stale));
    }

    @Test
    void effectiveRoleIdKeepsSuperAdminRoleForAdminEmail() {
        Staff owner = Staff.builder().email(ADMIN_EMAIL).roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID).build();

        assertEquals(RoleAuthorization.SUPER_ADMIN_ROLE_ID, RoleAuthorization.effectiveRoleId(server, owner));
    }

    @Test
    void assertCanAssignMinecraftPlayerAllowsSuperAdminForAnyTarget() {
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority(ADMIN_EMAIL, null, true, true);
        Staff target = Staff.builder().email("helper@example.com").build();

        assertDoesNotThrow(() -> roleAuthorization.assertCanAssignMinecraftPlayer(server, performer, target));
    }

    @Test
    void assertCanAssignMinecraftPlayerAllowsSelfByEmailIgnoringCase() {
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("Helper@Example.com", "helper", false, true);
        Staff target = Staff.builder().email("helper@example.com").build();

        assertDoesNotThrow(() -> roleAuthorization.assertCanAssignMinecraftPlayer(server, performer, target));
    }

    @Test
    void assertCanAssignMinecraftPlayerDeniesOtherTargetsForNonSuperAdmin() {
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("mgr@example.com", "mgr", false, true);
        Staff target = Staff.builder().email("helper@example.com").build();

        ForbiddenException error = assertThrows(ForbiddenException.class,
            () -> roleAuthorization.assertCanAssignMinecraftPlayer(server, performer, target));
        assertEquals(NO_AUTHORITY_MESSAGE, error.getMessage());
    }

    @Test
    void requireStaffManagePassesWhenRoleGrantsExactPermission() {
        givenRole("mgr", 1, "admin.staff.manage.roles");
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("mgr@example.com", "mgr", false, true);

        assertDoesNotThrow(() -> roleAuthorization.requireStaffManage(
            server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
    }

    @Test
    void requireStaffManagePassesWhenRoleGrantsViaParentPrefix() {
        givenRole("mgr", 1, "admin.staff.manage");
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("mgr@example.com", "mgr", false, true);

        assertDoesNotThrow(() -> roleAuthorization.requireStaffManage(
            server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
    }

    @Test
    void requireStaffManageDeniesWhenRoleLacksPermission() {
        givenRole("helper", 3, "ticket.reply.all");
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("helper@example.com", "helper", false, true);

        ForbiddenException error = assertThrows(ForbiddenException.class,
            () -> roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
        assertEquals(NO_AUTHORITY_MESSAGE, error.getMessage());
    }

    @Test
    void requireStaffManageDeniesResolvedPerformerWithNullRoleId() {
        givenStaff("staff-null-role", "orphan@example.com", null);
        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.minecraftPerformer(server, "staff-null-role");
        assertTrue(performer.identified());
        assertNull(performer.roleId());

        ForbiddenException error = assertThrows(ForbiddenException.class,
            () -> roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
        assertEquals(NO_AUTHORITY_MESSAGE, error.getMessage());
    }

    @Test
    void requireStaffManageDeniesUnidentifiedPerformer() {
        RoleAuthorization.PerformerAuthority performer = RoleAuthorization.PerformerAuthority.unidentified();

        assertThrows(ForbiddenException.class,
            () -> roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
    }

    @Test
    void requireStaffManageAllowsSuperAdminRegardlessOfRole() {
        RoleAuthorization.PerformerAuthority performer =
            new RoleAuthorization.PerformerAuthority("owner@example.com", null, true, true);

        assertDoesNotThrow(() -> roleAuthorization.requireStaffManage(
            server, performer, RoleAuthorization.MANAGE_ROLES_PERMISSION));
    }

    @Test
    void permissionedNonSuperAdminManagesStrictlyLowerRole() {
        givenStaff("staff-mgr", "mgr@example.com", "mgr");
        givenRole("mgr", 1, "admin.staff.manage.roles");
        StaffRole helper = givenRole("helper", 3, "ticket.view.all", "ticket.reply.all", "appeal.modify");
        when(roleRepository.saveEntity(eq(server), any())).thenAnswer(invocation -> invocation.getArgument(1));

        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.minecraftPerformer(server, "staff-mgr");
        boolean updated = roleService().updateRolePermissions(
            server, "helper", helper.getPermissions(), performer);

        assertTrue(updated);
    }

    @Test
    void permissionedNonSuperAdminCannotManageEqualOrHigherRole() {
        givenStaff("staff-mgr", "mgr@example.com", "mgr");
        givenRole("mgr", 2, "admin.staff.manage.roles");
        StaffRole peer = givenRole("peer", 2, "ticket.reply.all");
        StaffRole higher = givenRole("higher", 1, "ticket.reply.all");

        RoleAuthorization.PerformerAuthority performer =
            roleAuthorization.minecraftPerformer(server, "staff-mgr");
        RoleService roleService = roleService();

        ForbiddenException peerError = assertThrows(ForbiddenException.class,
            () -> roleService.updateRolePermissions(server, "peer", peer.getPermissions(), performer));
        assertEquals(HIGHER_AUTHORITY_MESSAGE, peerError.getMessage());

        ForbiddenException higherError = assertThrows(ForbiddenException.class,
            () -> roleService.updateRolePermissions(server, "higher", higher.getPermissions(), performer));
        assertEquals(HIGHER_AUTHORITY_MESSAGE, higherError.getMessage());
    }

    private RoleService roleService() {
        return new RoleService(
            roleRepository,
            staffRepository,
            permissionService,
            roleAuthorization,
            serverTimestampService);
    }

    private void givenStaff(String staffId, String email, String roleId) {
        Staff staff = Staff.builder().id(staffId).email(email).roleId(roleId).build();
        when(staffRepository.findById(server, staffId)).thenReturn(Optional.of(staff));
    }

    private StaffRole givenRole(String roleId, int order, String... permissions) {
        StaffRole role = StaffRole.builder()
            .id(roleId)
            .name(roleId)
            .order(order)
            .permissions(new ArrayList<>(List.of(permissions)))
            .build();
        when(roleRepository.findById(server, roleId)).thenReturn(Optional.of(role));
        return role;
    }
}
