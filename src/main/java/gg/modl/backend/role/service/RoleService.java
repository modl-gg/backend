package gg.modl.backend.role.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.fields.StaffRoleFields;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.dto.request.CreateRoleRequest;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.UpdateRoleRequest;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final StaffRoleMongoRepository staffRoleRepository;
    private final StaffMongoRepository staffRepository;
    private final PermissionService permissionService;
    private final ServerTimestampService serverTimestampService;

    public List<RoleResponse> getAllRoles(Server server) {
        // Fix any custom roles with incorrect ordering
        fixCustomRoleOrdering(server);

        // Get all roles sorted by order
        Query query = new Query().with(Sort.by(
                Sort.Direction.ASC,
                StaffRoleFields.ORDER.path(),
                StaffRoleFields.CREATED_AT.path()
        ));
        List<StaffRole> roles = staffRoleRepository.find(server, query);

        // Get staff counts per role
        Map<String, Integer> roleCounts = getStaffCountsByRole(server);

        return roles.stream()
                .map(role -> toRoleResponse(role, roleCounts.getOrDefault(role.getName(), 0)))
                .toList();
    }

    public Optional<RoleResponse> getRoleById(Server server, String id) {
        StaffRole role = staffRoleRepository.findById(server, id).orElse(null);

        if (role == null) {
            return Optional.empty();
        }

        int staffCount = getStaffCountForRole(server, role.getName());
        return Optional.of(toRoleResponse(role, staffCount));
    }

    public boolean updateRolePermissions(Server server, String id, List<String> permissions) {
        StaffRole role = staffRoleRepository.findById(server, id).orElse(null);
        if (role == null) {
            return false;
        }

        StaffRole original = staffRoleRepository.snapshot(role);
        role.setPermissions(permissions != null ? new ArrayList<>(permissions) : new ArrayList<>());
        role.setUpdatedAt(new Date());
        staffRoleRepository.saveChanges(server, original, role);
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public RoleResponse createRole(Server server, CreateRoleRequest request, String performerRoleName, boolean isSuperAdmin) {
        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(server, roleName, null);

        // Filter out any invalid permissions (e.g. from deleted punishment types)
        Set<String> validPermissions = new HashSet<>(permissionService.getAllPermissionIds(server));
        List<String> filteredPermissions = request.permissions().stream()
                .filter(validPermissions::contains)
                .toList();

        // Filter permissions to only those the performer can grant
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            filteredPermissions = filterToGrantablePermissions(performerRole, filteredPermissions);
        }

        // Generate unique ID
        String id = "custom-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Find highest order and add 1
        Query orderQuery = new Query().with(Sort.by(Sort.Direction.DESC, StaffRoleFields.ORDER.path())).limit(1);
        StaffRole highestRole = staffRoleRepository.findOne(server, orderQuery).orElse(null);
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

        return toRoleResponse(newRole, 0);
    }

    public Optional<RoleResponse> updateRole(Server server, String id, UpdateRoleRequest request, String performerRoleName, boolean isSuperAdmin) {
        // Cannot update Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot modify Super Admin role");
        }

        // Hierarchy check: performer must have higher authority than the target role
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            StaffRole targetRole = staffRoleRepository.findById(server, id).orElse(null);
            if (targetRole != null) {
                assertHigherAuthority(performerRole, targetRole);
            }
        }

        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(server, roleName, id);

        // Filter out any invalid permissions (e.g. from deleted punishment types)
        Set<String> validPermissions = new HashSet<>(permissionService.getAllPermissionIds(server));
        List<String> filteredPermissions = request.permissions().stream()
                .filter(validPermissions::contains)
                .toList();

        // Filter permissions to only those the performer can grant
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            filteredPermissions = filterToGrantablePermissions(performerRole, filteredPermissions);
        }

        StaffRole updated = staffRoleRepository.findById(server, id).orElse(null);

        if (updated == null) {
            return Optional.empty();
        }

        StaffRole original = staffRoleRepository.snapshot(updated);
        updated.setName(roleName);
        updated.setDescription(request.description());
        updated.setPermissions(new ArrayList<>(filteredPermissions));
        updated.setUpdatedAt(new Date());
        updated = staffRoleRepository.saveChanges(server, original, updated);

        serverTimestampService.updateStaffPermissionsTimestamp(server);

        int staffCount = getStaffCountForRole(server, updated.getName());
        return Optional.of(toRoleResponse(updated, staffCount));
    }

    public boolean deleteRole(Server server, String id, String performerRoleName, boolean isSuperAdmin) {
        // Cannot delete Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot delete Super Admin role");
        }

        // Check if any staff are using this role
        StaffRole role = staffRoleRepository.findById(server, id).orElse(null);

        if (role == null) {
            return false;
        }

        // Hierarchy check: performer must have higher authority than the target role
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            assertHigherAuthority(performerRole, role);
        }

        int staffCount = getStaffCountForRole(server, role.getName());
        if (staffCount > 0) {
            throw new IllegalStateException("Cannot delete role that is currently assigned to staff members");
        }

        Query deleteQuery = Query.query(MongoQueries.where(StaffRoleFields.ID).is(id));
        DeleteResult result = staffRoleRepository.remove(server, deleteQuery);

        return result.getDeletedCount() > 0;
    }

    public void reorderRoles(Server server, ReorderRolesRequest request, String performerRoleName, boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            int performerOrder = performerRole.getOrder();

            for (ReorderRolesRequest.RoleOrderItem item : request.roleOrder()) {
                // Look up the role being reordered
                StaffRole targetRole = staffRoleRepository.findById(server, item.id()).orElse(null);

                if (targetRole == null) continue;

                // Performer cannot reorder roles at or above their own authority level
                if (targetRole.getOrder() <= performerOrder) {
                    throw new IllegalArgumentException("You do not have authority to reorder this role");
                }

                // Cannot promote a role to the performer's level or above
                if (item.order() <= performerOrder) {
                    throw new IllegalArgumentException("You cannot promote a role to or above your own authority level");
                }
            }
        }

        request.roleOrder().forEach(item -> {
            Query query = Query.query(MongoQueries.where(StaffRoleFields.ID).is(item.id()));
            Update update = new Update();
            MongoUpdates.set(update, StaffRoleFields.ORDER, item.order());
            staffRoleRepository.updateFirst(server, query, update);
        });
    }

    // Default punishment permission nodes (punishment.apply.{name})
    private static final List<String> ALL_PUNISHMENT_PERMS = List.of(
            "punishment.apply.kick",
            "punishment.apply.manual-mute",
            "punishment.apply.manual-ban",
            "punishment.apply.security-ban",
            "punishment.apply.linked-ban",
            "punishment.apply.blacklist",
            "punishment.apply.chat-abuse",
            "punishment.apply.anti-social",
            "punishment.apply.targeting",
            "punishment.apply.bad-content",
            "punishment.apply.bad-username",
            "punishment.apply.bad-skin",
            "punishment.apply.team-abuse",
            "punishment.apply.game-abuse",
            "punishment.apply.cheating",
            "punishment.apply.game-trading",
            "punishment.apply.account-abuse",
            "punishment.apply.systems-abuse"
    );

    // Moderator gets all except blacklist
    private static final List<String> MODERATOR_PUNISHMENT_PERMS = ALL_PUNISHMENT_PERMS.stream()
            .filter(p -> !p.contains("blacklist"))
            .toList();

    public void createDefaultRoles(Server server) {
        // Super admin gets everything
        List<String> superAdminPerms = new ArrayList<>(permissionService.getAllPermissionIds(server));
        superAdminPerms.addAll(ALL_PUNISHMENT_PERMS);

        // Admin permissions
        List<String> adminPerms = new ArrayList<>(List.of(
                "admin.settings.view", "admin.staff.manage", "admin.audit.view",
                "punishment.modify",
                "ticket.view.all", "ticket.reply.all", "ticket.close.all",
                "staff.chat.toggle", "staff.chat.clear", "staff.chat.slow",
                "staff.maintenance", "staff.freeze", "staff.staffmode", "staff.vanish",
                "staff.target", "staff.intercept", "staff.chatlogs", "staff.commandlogs"
        ));
        adminPerms.addAll(ALL_PUNISHMENT_PERMS);

        // Moderator permissions
        List<String> moderatorPerms = new ArrayList<>(List.of(
                "punishment.modify",
                "ticket.view.all", "ticket.reply.all", "ticket.close.all",
                "staff.freeze", "staff.staffmode", "staff.vanish", "staff.target",
                "staff.chatlogs", "staff.commandlogs"
        ));
        moderatorPerms.addAll(MODERATOR_PUNISHMENT_PERMS);

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
                        .permissions(new ArrayList<>(List.of("ticket.view.all", "ticket.reply.all")))
                        .isDefault(true)
                        .order(3)
                        .createdAt(new Date())
                        .updatedAt(new Date())
                        .build()
        );

        for (StaffRole role : defaultRoles) {
            Query query = Query.query(MongoQueries.where(StaffRoleFields.ID).is(role.getId()));
            staffRoleRepository.upsert(server, query, buildUpdateFromRole(role));
        }
    }

    // --- Authorization helpers ---

    private StaffRole resolvePerformerRole(Server server, String roleName, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            // Synthetic super-admin role with order 0 and all permissions
            return StaffRole.builder()
                    .id("super-admin")
                    .name("Super Admin")
                    .permissions(new ArrayList<>(permissionService.getAllPermissionIds(server)))
                    .order(0)
                    .build();
        }
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("You do not have authority to perform this action");
        }
        return permissionService.getRoleByName(server, roleName)
                .orElseThrow(() -> new IllegalArgumentException("You do not have authority to perform this action"));
    }

    private boolean performerHasPermission(StaffRole performerRole, String permission) {
        if ("super-admin".equals(performerRole.getId()) || "Super Admin".equals(performerRole.getName())) {
            return true;
        }
        for (String perm : performerRole.getPermissions()) {
            if (perm.equals(permission)) return true;
            if (permission.startsWith(perm + ".")) return true;
        }
        return false;
    }

    private List<String> filterToGrantablePermissions(StaffRole performerRole, List<String> permissions) {
        return permissions.stream()
                .filter(p -> performerHasPermission(performerRole, p))
                .toList();
    }

    private void assertHigherAuthority(StaffRole performerRole, StaffRole targetRole) {
        if (performerRole.getOrder() >= targetRole.getOrder()) {
            throw new IllegalArgumentException("You do not have authority to modify a role at or above your own level");
        }
    }

    private void fixCustomRoleOrdering(Server server) {
        // Find custom roles with order 0 (should only be Super Admin)
        Query query = Query.query(MongoQueries.where(StaffRoleFields.IS_DEFAULT).is(false)
                .and(StaffRoleFields.ORDER.path()).is(0));
        List<StaffRole> problematicRoles = staffRoleRepository.find(server, query);

        if (!problematicRoles.isEmpty()) {
            // Find highest order
            Query orderQuery = new Query().with(Sort.by(Sort.Direction.DESC, StaffRoleFields.ORDER.path())).limit(1);
            StaffRole highestRole = staffRoleRepository.findOne(server, orderQuery).orElse(null);
            int nextOrder = highestRole != null ? Math.max(highestRole.getOrder(), 3) + 1 : 4;

            for (StaffRole role : problematicRoles) {
                Query updateQuery = Query.query(MongoQueries.where(StaffRoleFields.ID).is(role.getId()));
                Update update = new Update();
                MongoUpdates.set(update, StaffRoleFields.ORDER, nextOrder++);
                staffRoleRepository.updateFirst(server, updateQuery, update);
            }
        }
    }

    private Map<String, Integer> getStaffCountsByRole(Server server) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group(StaffFields.ROLE.path()).count().as("count")
        );

        AggregationResults<RoleCount> results = staffRepository.aggregate(server, aggregation, RoleCount.class);

        return results.getMappedResults().stream()
                .collect(Collectors.toMap(RoleCount::getId, RoleCount::getCount));
    }

    private int getStaffCountForRole(Server server, String roleName) {
        Query query = Query.query(MongoQueries.where(StaffFields.ROLE).is(roleName));
        return (int) staffRepository.count(server, query);
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

    private Update buildUpdateFromRole(StaffRole role) {
        Update update = new Update();
        MongoUpdates.set(update, StaffRoleFields.NAME, role.getName());
        MongoUpdates.set(update, StaffRoleFields.DESCRIPTION, role.getDescription());
        MongoUpdates.set(update, StaffRoleFields.PERMISSIONS, role.getPermissions());
        MongoUpdates.set(update, StaffRoleFields.IS_DEFAULT, role.isDefault());
        MongoUpdates.set(update, StaffRoleFields.ORDER, role.getOrder());
        MongoUpdates.setOnInsert(update, StaffRoleFields.CREATED_AT, role.getCreatedAt());
        MongoUpdates.set(update, StaffRoleFields.UPDATED_AT, role.getUpdatedAt());
        return update;
    }

    private void ensureRoleNameAvailable(Server server, String roleName, String excludeRoleId) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be empty");
        }

        Criteria criteria = MongoQueries.where(StaffRoleFields.NAME)
                .regex("^" + Pattern.quote(roleName) + "$", "i");
        if (excludeRoleId != null && !excludeRoleId.isBlank()) {
            criteria = criteria.and(StaffRoleFields.ID.path()).ne(excludeRoleId);
        }

        Query query = Query.query(criteria);
        if (staffRoleRepository.exists(server, query)) {
            throw new IllegalArgumentException("A role with this name already exists");
        }
    }

    // Helper class for aggregation results
    @lombok.Data
    private static class RoleCount {
        private String id;
        private int count;
    }
}
