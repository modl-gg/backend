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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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

        Query query = Query.query(Criteria.where("id").is(id));
        StaffRole role = template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return Optional.empty();
        }

        int staffCount = getStaffCountForRole(server, role.getName());
        return Optional.of(toRoleResponse(role, staffCount));
    }

    public RoleResponse createRole(Server server, CreateRoleRequest request) {
        MongoTemplate template = getTemplate(server);

        // Validate permissions
        List<String> validPermissions = permissionService.getAllPermissionIds(server);
        List<String> invalidPermissions = request.permissions().stream()
                .filter(p -> !validPermissions.contains(p))
                .toList();

        if (!invalidPermissions.isEmpty()) {
            throw new IllegalArgumentException("Invalid permissions: " + String.join(", ", invalidPermissions));
        }

        // Generate unique ID
        String id = "custom-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Find highest order and add 1
        Query orderQuery = new Query().with(Sort.by(Sort.Direction.DESC, "order")).limit(1);
        StaffRole highestRole = template.findOne(orderQuery, StaffRole.class, CollectionName.STAFF_ROLES);
        int nextOrder = highestRole != null ? highestRole.getOrder() + 1 : 4;

        StaffRole newRole = StaffRole.builder()
                .id(id)
                .name(request.name())
                .description(request.description())
                .permissions(new ArrayList<>(request.permissions()))
                .isDefault(false)
                .order(nextOrder)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(newRole, CollectionName.STAFF_ROLES);

        return toRoleResponse(newRole, 0);
    }

    public Optional<RoleResponse> updateRole(Server server, String id, UpdateRoleRequest request) {
        MongoTemplate template = getTemplate(server);

        // Cannot update Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot modify Super Admin role");
        }

        // Validate permissions
        List<String> validPermissions = permissionService.getAllPermissionIds(server);
        List<String> invalidPermissions = request.permissions().stream()
                .filter(p -> !validPermissions.contains(p))
                .toList();

        if (!invalidPermissions.isEmpty()) {
            throw new IllegalArgumentException("Invalid permissions: " + String.join(", ", invalidPermissions));
        }

        Query query = Query.query(Criteria.where("id").is(id));
        Update update = new Update()
                .set("name", request.name())
                .set("description", request.description())
                .set("permissions", request.permissions())
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

    public boolean deleteRole(Server server, String id) {
        MongoTemplate template = getTemplate(server);

        // Cannot delete Super Admin role
        if (id.contains("super-admin")) {
            throw new IllegalArgumentException("Cannot delete Super Admin role");
        }

        // Check if any staff are using this role
        Query roleQuery = Query.query(Criteria.where("id").is(id));
        StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return false;
        }

        int staffCount = getStaffCountForRole(server, role.getName());
        if (staffCount > 0) {
            throw new IllegalStateException("Cannot delete role that is currently assigned to staff members");
        }

        Query deleteQuery = Query.query(Criteria.where("id").is(id));
        DeleteResult result = template.remove(deleteQuery, StaffRole.class, CollectionName.STAFF_ROLES);

        return result.getDeletedCount() > 0;
    }

    public void reorderRoles(Server server, ReorderRolesRequest request) {
        MongoTemplate template = getTemplate(server);

        request.roleOrder().forEach(item -> {
            Query query = Query.query(Criteria.where("id").is(item.id()));
            Update update = new Update().set("order", item.order());
            template.updateFirst(query, update, StaffRole.class, CollectionName.STAFF_ROLES);
        });
    }

    public void createDefaultRoles(Server server) {
        MongoTemplate template = getTemplate(server);

        List<String> allPermissionIds = permissionService.getAllPermissionIds(server);
        List<String> punishmentPerms = permissionService.getPunishmentPermissions(server)
                .stream().map(p -> p.id()).toList();

        List<StaffRole> defaultRoles = List.of(
                StaffRole.builder()
                        .id("super-admin")
                        .name("Super Admin")
                        .description("Full access to all features and settings")
                        .permissions(new ArrayList<>(allPermissionIds))
                        .isDefault(true)
                        .order(0)
                        .createdAt(new Date())
                        .updatedAt(new Date())
                        .build(),
                StaffRole.builder()
                        .id("admin")
                        .name("Admin")
                        .description("Administrative access with some restrictions")
                        .permissions(new ArrayList<>(List.of(
                                "admin.settings.view", "admin.staff.manage", "admin.audit.view",
                                "punishment.modify",
                                "ticket.view.all", "ticket.reply.all", "ticket.close.all"
                        )))
                        .isDefault(true)
                        .order(1)
                        .createdAt(new Date())
                        .updatedAt(new Date())
                        .build(),
                StaffRole.builder()
                        .id("moderator")
                        .name("Moderator")
                        .description("Moderation permissions for punishments and tickets")
                        .permissions(new ArrayList<>(List.of(
                                "punishment.modify",
                                "ticket.view.all", "ticket.reply.all", "ticket.close.all"
                        )))
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

        // Add punishment permissions to admin role
        defaultRoles.get(1).getPermissions().addAll(punishmentPerms);

        // Add filtered punishment permissions to moderator (exclude blacklist/security-ban)
        List<String> moderatorPunishPerms = punishmentPerms.stream()
                .filter(p -> !p.contains("blacklist") && !p.contains("security-ban"))
                .toList();
        defaultRoles.get(2).getPermissions().addAll(moderatorPunishPerms);

        for (StaffRole role : defaultRoles) {
            Query query = Query.query(Criteria.where("id").is(role.getId()));
            template.upsert(query, buildUpdateFromRole(role), CollectionName.STAFF_ROLES);
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
                Query updateQuery = Query.query(Criteria.where("mongoId").is(role.getMongoId()));
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
                .set("id", role.getId())
                .set("name", role.getName())
                .set("description", role.getDescription())
                .set("permissions", role.getPermissions())
                .set("isDefault", role.isDefault())
                .set("order", role.getOrder())
                .setOnInsert("createdAt", role.getCreatedAt())
                .set("updatedAt", role.getUpdatedAt());
    }

    // Helper class for aggregation results
    @lombok.Data
    private static class RoleCount {
        private String id;
        private int count;
    }
}
