package gg.modl.backend.role.service;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PunishmentTypeService punishmentTypeService;

    private static final List<Permission> BASE_PERMISSIONS = List.of(
            // Admin permissions
            new Permission("admin.settings.view", "View Settings", "View all system settings", "admin"),
            new Permission("admin.settings.modify", "Modify Settings", "Modify system settings (excluding account settings)", "admin"),
            new Permission("admin.staff.manage", "Manage Staff", "Invite, remove, and modify staff members", "admin"),
            new Permission("admin.audit.view", "View Audit", "Access audit logs and system activity", "admin"),

            // Punishment permissions
            new Permission("punishment.modify", "Modify Punishments", "Pardon, modify duration, and edit existing punishments", "punishment"),

            // Ticket permissions
            new Permission("ticket.view.all", "View All Tickets", "View all tickets regardless of type", "ticket"),
            new Permission("ticket.reply.all", "Reply to All Tickets", "Reply to all ticket types", "ticket"),
            new Permission("ticket.close.all", "Close/Reopen All Tickets", "Close and reopen all ticket types", "ticket"),
            new Permission("ticket.delete.all", "Delete Tickets", "Delete tickets from the system", "ticket")
    );

    private static final Map<String, String> PERMISSION_CATEGORIES = Map.of(
            "punishment", "Punishment Permissions",
            "ticket", "Ticket Permissions",
            "admin", "Administrative Permissions"
    );

    public List<Permission> getBasePermissions() {
        return BASE_PERMISSIONS;
    }

    public Map<String, String> getPermissionCategories() {
        return PERMISSION_CATEGORIES;
    }

    public List<Permission> getPunishmentPermissions(Server server) {
        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        List<Permission> permissions = new ArrayList<>();

        punishmentTypes.forEach(type -> {
            String permId = "punishment.apply." + type.getName().toLowerCase().replace(" ", "-");
            permissions.add(new Permission(
                permId,
                "Apply " + type.getName(),
                "Permission to apply " + type.getName() + " punishments",
                "punishment"
            ));
        });

        return permissions;
    }

    public List<Permission> getAllPermissions(Server server) {
        List<Permission> all = new ArrayList<>(BASE_PERMISSIONS);
        all.addAll(getPunishmentPermissions(server));
        return all;
    }

    public List<String> getAllPermissionIds(Server server) {
        return getAllPermissions(server).stream().map(Permission::id).toList();
    }

    public boolean hasPermission(Server server, String staffRole, String permission) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("name").is(staffRole));
        StaffRole role = template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return false;
        }

        // Super Admin has all permissions
        if ("super-admin".equals(role.getId())) {
            return true;
        }

        return role.getPermissions().contains(permission);
    }

    public Optional<StaffRole> getRoleByName(Server server, String roleName) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());
        Query query = Query.query(Criteria.where("name").is(roleName));
        return Optional.ofNullable(template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES));
    }

    public boolean isSuperAdmin(Server server, String staffEmail) {
        // Super Admin is the server admin email
        return server.getAdminEmail() != null &&
                server.getAdminEmail().equalsIgnoreCase(staffEmail);
    }
}
