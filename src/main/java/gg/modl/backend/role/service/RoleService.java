package gg.modl.backend.role.service;

import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.RoleRequest;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final StaffRoleMongoRepository staffRoleRepository;
    private final StaffMongoRepository staffRepository;
    private final PermissionService permissionService;
    private final ServerTimestampService serverTimestampService;

    private static final String SUPER_ADMIN_ROLE_ID = "super-admin";

    public List<RoleResponse> getAllRoles(Server server) {
        fixCustomRoleOrdering(server);

        List<StaffRole> roles = staffRoleRepository.findAllOrdered(server);
        Map<String, Integer> roleCounts = staffRepository.countByRoleId(server);

        return roles.stream()
            .map(role -> toRoleResponse(role, roleCounts.getOrDefault(role.getId(), 0)))
            .toList();
    }

    private void fixCustomRoleOrdering(Server server) {
        List<StaffRole> problematicRoles = staffRoleRepository.findCustomRolesWithOrderZero(server);
        if (problematicRoles.isEmpty()) {
            return;
        }

        StaffRole highestRole = staffRoleRepository.findHighestOrdered(server).orElse(null);
        int baseOrder = highestRole != null ? Math.max(highestRole.getOrder(), 3) + 1 : 4;

        // Assign orders deterministically so concurrent readers compute the same target per role
        // and the compare-and-set repair (matches order == 0) cannot create cross-role duplicates.
        List<StaffRole> sorted = problematicRoles.stream()
            .sorted(Comparator.comparing(StaffRole::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StaffRole::getId))
            .toList();

        Map<String, Integer> orderById = new LinkedHashMap<>();
        int nextOrder = baseOrder;
        for (StaffRole role : sorted) {
            orderById.put(role.getId(), nextOrder++);
        }
        staffRoleRepository.bulkRepairOrderFromZero(server, orderById);
    }

    private RoleResponse toRoleResponse(StaffRole role, int userCount) {
        return new RoleResponse(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.getPermissions(),
            role.isDefault(),
            role.getOrder(),
            userCount,
            role.getCreatedAt(),
            role.getUpdatedAt()
        );
    }

    public Optional<RoleResponse> getRoleById(Server server, String id) {
        StaffRole role = staffRoleRepository.findById(server, id).orElse(null);

        if (role == null) {
            return Optional.empty();
        }

        int staffCount = getStaffCountForRole(server, role.getId());
        return Optional.of(toRoleResponse(role, staffCount));
    }

    private int getStaffCountForRole(Server server, String roleId) {
        return staffRepository.countByRoleId(server, roleId);
    }

    public boolean updateRolePermissions(Server server, String id, List<String> permissions,
                                         String performerRoleId, boolean isSuperAdmin, boolean hasPerformerIdentity) {
        // Super Admin role permissions are immutable (mirror updateRole/deleteRole).
        if (id != null && id.contains(SUPER_ADMIN_ROLE_ID)) {
            throw new ForbiddenException("Cannot modify Super Admin role");
        }

        StaffRole targetRole = staffRoleRepository.findById(server, id).orElse(null);
        if (targetRole == null) {
            return false;
        }

        // Drop unknown/stale permission ids (matches createRole/updateRole).
        Set<String> validPermissions = new HashSet<>(permissionService.getAllPermissionIds(server));
        List<String> requested = permissions != null ? permissions : List.of();
        List<String> filtered = requested.stream()
            .filter(validPermissions::contains)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));

        // When the caller carries a trustworthy performer identity (panel/header), enforce hierarchy and
        // grantability so a lower-tier actor cannot escalate. Pre-existing higher perms are preserved
        // (this whole-list-replace endpoint must not silently DOWNGRADE perms the performer cannot grant).
        if (hasPerformerIdentity && !isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            assertHigherAuthority(performerRole, targetRole);
            Set<String> existing = new HashSet<>(targetRole.getPermissions() != null
                ? targetRole.getPermissions() : List.of());
            filtered = filtered.stream()
                .filter(p -> performerHasPermission(performerRole, p) || existing.contains(p))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        targetRole.setPermissions(filtered);
        targetRole.setUpdatedAt(new Date());
        staffRoleRepository.saveEntity(server, targetRole);
        invalidatePermissionState(server);
        return true;
    }

    private void invalidatePermissionState(Server server) {
        permissionService.evictPermissionCache();
        serverTimestampService.updateStaffPermissionsTimestamp(server);
    }

    public RoleResponse createRole(Server server, RoleRequest request, String performerRoleId, boolean isSuperAdmin) {
        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(server, roleName, null);

        // Filter out any invalid permissions (e.g. from deleted punishment types)
        Set<String> validPermissions = new HashSet<>(permissionService.getAllPermissionIds(server));
        List<String> filteredPermissions = request.permissions()
            .stream()
            .filter(validPermissions::contains)
            .toList();

        // Filter permissions to only those the performer can grant
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            filteredPermissions = filterToGrantablePermissions(performerRole, filteredPermissions);
        }

        // Generate unique ID
        String id = "custom-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Find highest order and add 1
        StaffRole highestRole = staffRoleRepository.findHighestOrdered(server).orElse(null);
        int nextOrder = highestRole != null ? highestRole.getOrder() + 1 : 4;

        StaffRole newRole = StaffRole.builder()
            .id(id)
            .name(roleName)
            .description(request.description())
            .permissions(new ArrayList<>(filteredPermissions))
            .isDefault(false)
            .order(nextOrder)
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        staffRoleRepository.saveEntity(server, newRole);
        // No permission-state invalidation: a new role has no staff assigned and no cached entries;
        // the bump happens on staff assignment via StaffService.

        return toRoleResponse(newRole, 0);
    }

    private StaffRole resolvePerformerRole(Server server, String roleId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            // Synthetic super-admin role with order 0 and all permissions
            return StaffRole.builder()
                .id("super-admin")
                .name("Super Admin")
                .permissions(new ArrayList<>(permissionService.getAllPermissionIds(server)))
                .order(0)
                .build();
        }
        if (roleId == null || roleId.isBlank()) {
            throw new ForbiddenException("You do not have authority to perform this action");
        }
        return permissionService.getRoleById(server, roleId)
            .orElseThrow(() -> new ForbiddenException("You do not have authority to perform this action"));
    }

    private List<String> filterToGrantablePermissions(StaffRole performerRole, List<String> permissions) {
        return permissions.stream()
            .filter(p -> performerHasPermission(performerRole, p))
            .toList();
    }

    private boolean performerHasPermission(StaffRole performerRole, String permission) {
        if ("super-admin".equals(performerRole.getId()) || "Super Admin".equals(performerRole.getName())) {
            return true;
        }
        for (String perm : performerRole.getPermissions()) {
            if (perm.equals(permission)) {
                return true;
            }
            if (permission.startsWith(perm + ".")) {
                return true;
            }
        }
        return false;
    }

    private void ensureRoleNameAvailable(Server server, String roleName, String excludeRoleId) {
        if (roleName == null || roleName.isBlank()) {
            throw new ValidationException("Role name cannot be empty");
        }

        boolean exists = excludeRoleId != null && !excludeRoleId.isBlank()
                         ? staffRoleRepository.existsByNameIgnoreCaseExcludingId(server, roleName, excludeRoleId)
                         : staffRoleRepository.existsByNameIgnoreCase(server, roleName);
        if (exists) {
            throw new ConflictException("A role with this name already exists");
        }
    }

    public Optional<RoleResponse> updateRole(Server server, String id, RoleRequest request, String performerRoleId, boolean isSuperAdmin) {
        // Cannot update Super Admin role
        if (SUPER_ADMIN_ROLE_ID.equals(id)) {
            throw new ForbiddenException("Cannot modify Super Admin role");
        }

        // Hierarchy check: performer must have higher authority than the target role
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            StaffRole targetRole = staffRoleRepository.findById(server, id).orElse(null);
            if (targetRole != null) {
                assertHigherAuthority(performerRole, targetRole);
            }
        }

        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(server, roleName, id);

        // Filter out any invalid permissions (e.g. from deleted punishment types)
        Set<String> validPermissions = new HashSet<>(permissionService.getAllPermissionIds(server));
        List<String> filteredPermissions = request.permissions()
            .stream()
            .filter(validPermissions::contains)
            .toList();

        // Filter permissions to only those the performer can grant
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            filteredPermissions = filterToGrantablePermissions(performerRole, filteredPermissions);
        }

        StaffRole updated = staffRoleRepository.findById(server, id).orElse(null);

        if (updated == null) {
            return Optional.empty();
        }

        updated.setName(roleName);
        updated.setDescription(request.description());
        updated.setPermissions(new ArrayList<>(filteredPermissions));
        updated.setUpdatedAt(new Date());
        updated = staffRoleRepository.saveEntity(server, updated);
        invalidatePermissionState(server);

        int staffCount = getStaffCountForRole(server, updated.getId());
        return Optional.of(toRoleResponse(updated, staffCount));
    }

    private void assertHigherAuthority(StaffRole performerRole, StaffRole targetRole) {
        if (performerRole.getOrder() >= targetRole.getOrder()) {
            throw new ForbiddenException("You do not have authority to modify a role at or above your own level");
        }
    }

    public boolean deleteRole(Server server, String id, String performerRoleId, boolean isSuperAdmin) {
        // Cannot delete Super Admin role
        if (SUPER_ADMIN_ROLE_ID.equals(id)) {
            throw new ForbiddenException("Cannot delete Super Admin role");
        }

        // Check if any staff are using this role
        StaffRole role = staffRoleRepository.findById(server, id).orElse(null);

        if (role == null) {
            return false;
        }

        // Hierarchy check: performer must have higher authority than the target role
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            assertHigherAuthority(performerRole, role);
        }

        int staffCount = getStaffCountForRole(server, role.getId());
        if (staffCount > 0) {
            throw new ConflictException("Cannot delete role that is currently assigned to staff members");
        }

        boolean deleted = staffRoleRepository.deleteById(server, id);
        if (deleted) {
            invalidatePermissionState(server);
        }
        return deleted;
    }

    public void reorderRoles(Server server, ReorderRolesRequest request, String performerRoleId, boolean isSuperAdmin) {
        List<ReorderRolesRequest.RoleOrderItem> items = request.roleOrder();
        if (items.isEmpty()) return;

        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleId, false);
            int performerOrder = performerRole.getOrder();

            List<String> ids = items.stream().map(ReorderRolesRequest.RoleOrderItem::id).toList();
            Map<String, StaffRole> rolesById = staffRoleRepository.findByIds(server, ids)
                .stream()
                .collect(Collectors.toMap(StaffRole::getId, Function.identity()));

            for (ReorderRolesRequest.RoleOrderItem item : items) {
                StaffRole targetRole = rolesById.get(item.id());
                if (targetRole == null) continue;

                if (targetRole.getOrder() <= performerOrder) {
                    throw new ForbiddenException("You do not have authority to reorder this role");
                }

                if (item.order() <= performerOrder) {
                    throw new ForbiddenException("You cannot promote a role to or above your own authority level");
                }
            }
        }

        Map<String, Integer> orderById = new LinkedHashMap<>();
        for (ReorderRolesRequest.RoleOrderItem item : items) {
            orderById.put(item.id(), item.order());
        }
        staffRoleRepository.bulkUpdateOrder(server, orderById);
        // Reorder changes grant-hierarchy authority the plugin displays; bump the timestamp so it re-syncs.
        // No cache evict needed: permissionCache keys are order-independent (serverId:roleId:permission).
        serverTimestampService.updateStaffPermissionsTimestamp(server);
    }

    public void createDefaultRoles(Server server) {
        List<String> allPunishmentPerms = permissionService.getPunishmentPermissions(server)
            .stream()
            .map(Permission::id)
            .toList();
        List<String> moderatorPunishmentPerms = allPunishmentPerms.stream()
            .filter(p -> !p.contains("blacklist"))
            .toList();

        List<String> superAdminPerms = new ArrayList<>(permissionService.getAllPermissionIds(server));

        List<String> adminPerms = new ArrayList<>(List.of(
            "admin.settings.view", "admin.staff.manage", "admin.audit.view",
            "punishment.view", "punishment.modify",
            "ticket.view.all", "ticket.reply.all", "appeal.modify", "ticket.close.all",
            "staff.chat.toggle", "staff.chat.clear", "staff.chat.slow",
            "staff.maintenance", "staff.modactions",
            "staff.intercept", "staff.chatlogs", "staff.commandlogs"
        ));
        adminPerms.addAll(allPunishmentPerms);

        List<String> moderatorPerms = new ArrayList<>(List.of(
            "punishment.view", "punishment.modify",
            "ticket.view.all", "ticket.reply.all", "appeal.modify", "ticket.close.all",
            "staff.modactions",
            "staff.chatlogs", "staff.commandlogs"
        ));
        moderatorPerms.addAll(moderatorPunishmentPerms);

        List<StaffRole> defaultRoles = List.of(
            StaffRole.builder()
                .id("super-admin")
                .name("Super Admin")
                .description("Full access to all features and settings")
                .permissions(superAdminPerms)
                .isDefault(true)
                .order(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build(),
            StaffRole.builder()
                .id("admin")
                .name("Admin")
                .description("Administrative access with some restrictions")
                .permissions(adminPerms)
                .isDefault(true)
                .order(1)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build(),
            StaffRole.builder()
                .id("moderator")
                .name("Moderator")
                .description("Moderation permissions for punishments and tickets")
                .permissions(moderatorPerms)
                .isDefault(true)
                .order(2)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build(),
            StaffRole.builder()
                .id("helper")
                .name("Helper")
                .description("Basic support permissions")
                .permissions(new ArrayList<>(List.of("ticket.view.all", "ticket.reply.all", "appeal.modify")))
                .isDefault(true)
                .order(3)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build()
        );

        for (StaffRole role : defaultRoles) {
            staffRoleRepository.upsertRole(server, role);
        }
        permissionService.evictPermissionCache();
    }
}
