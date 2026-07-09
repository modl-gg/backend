package gg.modl.backend.role.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleAuthorization {
    public static final String SUPER_ADMIN_ROLE_ID = "super-admin";
    public static final String MANAGE_MEMBERS_PERMISSION = "admin.staff.manage.members";
    public static final String MANAGE_ROLES_PERMISSION = "admin.staff.manage.roles";

    private static final String SUPER_ADMIN_ROLE_NAME = "Super Admin";
    private static final String NO_AUTHORITY_MESSAGE = "You do not have authority to perform this action";
    private static final String NO_GRANT_AUTHORITY_MESSAGE = "You do not have authority to grant this role";
    private static final String HIGHER_AUTHORITY_MESSAGE = "You do not have authority over a role at or above your own level";
    private static final String PROTECTED_ADMIN_MESSAGE = "Cannot modify the server administrator";

    private final PermissionService permissionService;
    private final StaffMongoRepository staffRepository;

    public record PerformerAuthority(String email, String roleId, boolean superAdmin, boolean identified) {
        public static PerformerAuthority unidentified() {
            return new PerformerAuthority(null, null, false, false);
        }
    }

    public PerformerAuthority panelPerformer(Server server, @Nullable String sessionEmail) {
        if (sessionEmail == null || sessionEmail.isBlank()) {
            return PerformerAuthority.unidentified();
        }
        if (isSuperAdminEmail(server, sessionEmail)) {
            return new PerformerAuthority(sessionEmail, null, true, true);
        }
        String roleId = staffRepository.findByEmailIgnoreCase(server, sessionEmail)
            .map(staff -> effectiveRoleId(server, staff))
            .orElse(null);
        return new PerformerAuthority(sessionEmail, roleId, false, true);
    }

    public PerformerAuthority minecraftPerformer(Server server, @Nullable String actingStaffId) {
        if (actingStaffId == null || actingStaffId.isBlank()) {
            return PerformerAuthority.unidentified();
        }
        return staffRepository.findById(server, actingStaffId)
            .map(staff -> new PerformerAuthority(staff.getEmail(), effectiveRoleId(server, staff),
                isSuperAdminEmail(server, staff.getEmail()), true))
            .orElseGet(PerformerAuthority::unidentified);
    }

    public void requireStaffManage(Server server, PerformerAuthority performer, String requiredManagePermission) {
        if (!performer.identified()) {
            throw new ForbiddenException(NO_AUTHORITY_MESSAGE);
        }
        if (performer.superAdmin()) {
            return;
        }
        if (performer.roleId() == null
            || !permissionService.hasPermission(server, performer.roleId(), requiredManagePermission)) {
            throw new ForbiddenException(NO_AUTHORITY_MESSAGE);
        }
    }

    public void assertCanAssignMinecraftPlayer(Server server, PerformerAuthority performer, Staff target) {
        if (performer.superAdmin()) {
            return;
        }
        if (performer.email() != null && performer.email().equalsIgnoreCase(target.getEmail())) {
            return;
        }
        if (performer.roleId() != null
            && permissionService.hasPermission(server, performer.roleId(), MANAGE_MEMBERS_PERMISSION)) {
            return;
        }
        throw new ForbiddenException(NO_AUTHORITY_MESSAGE);
    }

    public void assertCanActOnStaff(Server server, PerformerAuthority performer, Staff target) {
        if (isSuperAdminEmail(server, target.getEmail())) {
            throw new ForbiddenException(PROTECTED_ADMIN_MESSAGE);
        }
        if (performer.superAdmin()) {
            return;
        }
        StaffRole performerRole = requirePerformerRole(server, performer);
        StaffRole targetRole = permissionService.getRoleById(server, target.getRoleId())
            .orElseThrow(() -> new ForbiddenException(HIGHER_AUTHORITY_MESSAGE));
        assertHigherAuthority(performerRole, targetRole);
    }

    public StaffRole assertGrantableRole(Server server, PerformerAuthority performer, String targetRoleName) {
        StaffRole targetRole = permissionService.getRoleByName(server, targetRoleName)
            .orElseThrow(() -> new ValidationException("Unknown staff role"));
        if (isSuperAdminRole(targetRole)) {
            throw new ForbiddenException(NO_GRANT_AUTHORITY_MESSAGE);
        }
        if (performer.superAdmin()) {
            return targetRole;
        }
        StaffRole performerRole = requirePerformerRole(server, performer);
        assertHigherAuthority(performerRole, targetRole);
        return targetRole;
    }

    public StaffRole requirePerformerRole(Server server, PerformerAuthority performer) {
        if (!performer.identified() || performer.roleId() == null || performer.roleId().isBlank()) {
            throw new ForbiddenException(NO_AUTHORITY_MESSAGE);
        }
        return permissionService.getRoleById(server, performer.roleId())
            .orElseThrow(() -> new ForbiddenException(NO_AUTHORITY_MESSAGE));
    }

    public void assertHigherAuthority(StaffRole performer, StaffRole target) {
        if (performer.getOrder() >= target.getOrder()) {
            throw new ForbiddenException(HIGHER_AUTHORITY_MESSAGE);
        }
    }

    public static boolean roleGrants(StaffRole role, String permission) {
        if (isSuperAdminRole(role)) {
            return true;
        }
        List<String> permissions = role.getPermissions();
        if (permissions == null) {
            return false;
        }
        for (String held : permissions) {
            if (permissionImplies(held, permission)) {
                return true;
            }
        }
        return false;
    }

    public static boolean permissionImplies(String heldPermission, String requestedPermission) {
        return heldPermission.equals(requestedPermission)
            || requestedPermission.startsWith(heldPermission + ".");
    }

    public static boolean isSuperAdminRole(StaffRole role) {
        return SUPER_ADMIN_ROLE_ID.equals(role.getId()) || SUPER_ADMIN_ROLE_NAME.equals(role.getName());
    }

    public static boolean isSuperAdminRoleId(String roleId) {
        return SUPER_ADMIN_ROLE_ID.equals(roleId);
    }

    public static boolean isSuperAdminEmail(Server server, String email) {
        return server.getAdminEmail() != null && server.getAdminEmail().equalsIgnoreCase(email);
    }

    public static String effectiveRoleId(Server server, Staff staff) {
        String roleId = staff.getRoleId();
        if (SUPER_ADMIN_ROLE_ID.equals(roleId) && !isSuperAdminEmail(server, staff.getEmail())) {
            return null;
        }
        return roleId;
    }
}
