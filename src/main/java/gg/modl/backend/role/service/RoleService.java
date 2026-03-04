package gg.modl.backend.role.service;

import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.dto.request.CreateRoleRequest;
import gg.modl.backend.role.dto.request.ReorderRolesRequest;
import gg.modl.backend.role.dto.request.UpdateRoleRequest;
import gg.modl.backend.role.dto.response.RoleResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PermissionService permissionService;
    private final ServerTimestampService serverTimestampService;

    public List<RoleResponse> getAllRoles(Server server) {
        MongoTemplate template = getTemplate(server);

        // Fix any custom roles with incorrect ordering
        fixCustomRoleOrdering(server);

        // Get all roles sorted by order
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "order", "createdAt"));
        List<StaffRole> roles = template.find(query, StaffRole.class, CollectionName.STAFF_ROLES);

        // Get staff counts per role
        Map<String, Integer> roleCounts = getStaffCountsByRole(server);

        return roles.stream()
                .map(role -> toRoleResponse(role, roleCounts.getOrDefault(role.getName(), 0)))
                .toList();
    }

    public Optional<RoleResponse> getRoleById(Server server, String id) {
        MongoTemplate template = getTemplate(server);

        Query query = Query.query(Criteria.where("_id").is(id));
        StaffRole role = template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return Optional.empty();
        }

        int staffCount = getStaffCountForRole(server, role.getName());
        return Optional.of(toRoleResponse(role, staffCount));
    }

    public RoleResponse createRole(Server server, CreateRoleRequest request, String performerRoleName, boolean isSuperAdmin) {
        MongoTemplate template = getTemplate(server);
        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(template, roleName, null);

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
        Query orderQuery = new Query().with(Sort.by(Sort.Direction.DESC, "order")).limit(1);
        StaffRole highestRole = template.findOne(orderQuery, StaffRole.class, CollectionName.STAFF_ROLES);
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

        template.save(newRole, CollectionName.STAFF_ROLES);

        return toRoleResponse(newRole, 0);
    }

    public Optional<RoleResponse> updateRole(Server server, String id, UpdateRoleRequest request, String performerRoleName, boolean isSuperAdmin) {
        MongoTemplate template = getTemplate(server);

        // Cannot update Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot modify Super Admin role");
        }

        // Hierarchy check: performer must have higher authority than the target role
        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            Query targetQuery = Query.query(Criteria.where("_id").is(id));
            StaffRole targetRole = template.findOne(targetQuery, StaffRole.class, CollectionName.STAFF_ROLES);
            if (targetRole != null) {
                assertHigherAuthority(performerRole, targetRole);
            }
        }

        String roleName = request.name() != null ? request.name().trim() : "";
        ensureRoleNameAvailable(template, roleName, id);

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

        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("name", roleName)
                .set("description", request.description())
                .set("permissions", filteredPermissions)
                .set("updatedAt", new Date());

        StaffRole updated = template.findAndModify(query, update,
                new org.springframework.data.mongodb.core.FindAndModifyOptions().returnNew(true),
                StaffRole.class, CollectionName.STAFF_ROLES);

        if (updated == null) {
            return Optional.empty();
        }

        serverTimestampService.updateStaffPermissionsTimestamp(server);

        int staffCount = getStaffCountForRole(server, updated.getName());
        return Optional.of(toRoleResponse(updated, staffCount));
    }

    public boolean deleteRole(Server server, String id, String performerRoleName, boolean isSuperAdmin) {
        MongoTemplate template = getTemplate(server);

        // Cannot delete Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot delete Super Admin role");
        }

        // Check if any staff are using this role
        Query roleQuery = Query.query(Criteria.where("_id").is(id));
        StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

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

        Query deleteQuery = Query.query(Criteria.where("_id").is(id));
        DeleteResult result = template.remove(deleteQuery, StaffRole.class, CollectionName.STAFF_ROLES);

        return result.getDeletedCount() > 0;
    }

    public void reorderRoles(Server server, ReorderRolesRequest request, String performerRoleName, boolean isSuperAdmin) {
        MongoTemplate template = getTemplate(server);

        if (!isSuperAdmin) {
            StaffRole performerRole = resolvePerformerRole(server, performerRoleName, false);
            int performerOrder = performerRole.getOrder();

            for (ReorderRolesRequest.RoleOrderItem item : request.roleOrder()) {
                // Look up the role being reordered
                Query roleQuery = Query.query(Criteria.where("_id").is(item.id()));
                StaffRole targetRole = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

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
            Query query = Query.query(Criteria.where("_id").is(item.id()));
            Update update = new Update().set("order", item.order());
            template.updateFirst(query, update, StaffRole.class, CollectionName.STAFF_ROLES);
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
        MongoTemplate template = getTemplate(server);

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
            Query query = Query.query(Criteria.where("_id").is(role.getId()));
            template.upsert(query, buildUpdateFromRole(role), CollectionName.STAFF_ROLES);
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
        MongoTemplate template = getTemplate(server);

        // Find custom roles with order 0 (should only be Super Admin)
        Query query = Query.query(Criteria.where("isDefault").is(false).and("order").is(0));
        List<StaffRole> problematicRoles = template.find(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (!problematicRoles.isEmpty()) {
            // Find highest order
            Query orderQuery = new Query().with(Sort.by(Sort.Direction.DESC, "order")).limit(1);
            StaffRole highestRole = template.findOne(orderQuery, StaffRole.class, CollectionName.STAFF_ROLES);
            int nextOrder = highestRole != null ? Math.max(highestRole.getOrder(), 3) + 1 : 4;

            for (StaffRole role : problematicRoles) {
                Query updateQuery = Query.query(Criteria.where("_id").is(role.getId()));
                Update update = new Update().set("order", nextOrder++);
                template.updateFirst(updateQuery, update, StaffRole.class, CollectionName.STAFF_ROLES);
            }
        }
    }

    private Map<String, Integer> getStaffCountsByRole(Server server) {
        MongoTemplate template = getTemplate(server);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("role").count().as("count")
        );

        AggregationResults<RoleCount> results = template.aggregate(
                aggregation, CollectionName.STAFF, RoleCount.class);

        return results.getMappedResults().stream()
                .collect(Collectors.toMap(RoleCount::getId, RoleCount::getCount));
    }

    private int getStaffCountForRole(Server server, String roleName) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("role").is(roleName));
        return (int) template.count(query, CollectionName.STAFF);
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
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
        return new Update()
                .set("name", role.getName())
                .set("description", role.getDescription())
                .set("permissions", role.getPermissions())
                .set("isDefault", role.isDefault())
                .set("order", role.getOrder())
                .setOnInsert("createdAt", role.getCreatedAt())
                .set("updatedAt", role.getUpdatedAt());
    }

    private void ensureRoleNameAvailable(MongoTemplate template, String roleName, String excludeRoleId) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be empty");
        }

        Criteria criteria = Criteria.where("name")
                .regex("^" + Pattern.quote(roleName) + "$", "i");
        if (excludeRoleId != null && !excludeRoleId.isBlank()) {
            criteria = criteria.and("_id").ne(excludeRoleId);
        }

        Query query = Query.query(criteria);
        if (template.exists(query, StaffRole.class, CollectionName.STAFF_ROLES)) {
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
