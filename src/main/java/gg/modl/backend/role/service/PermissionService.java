package gg.modl.backend.role.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.role.data.Permission;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.PunishmentTypeService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final StaffRoleMongoRepository staffRoleRepository;
    private final PunishmentTypeService punishmentTypeService;
    private final StaffMongoRepository staffRepository;

    private final Cache<String, Boolean> permissionCache = Caffeine.newBuilder()
        .maximumSize(2000)
        .expireAfterWrite(Duration.ofMinutes(2))
        .build();

    private static final List<Permission> BASE_PERMISSIONS = List.of(
        // Admin permissions
        new Permission("admin.settings.view", "View Settings", "View all system settings (includes all sub-permissions)", "admin"),
        new Permission("admin.settings.view.punishments", "View Punishments Config", "View punishment type configuration", "admin", "admin.settings.view"),
        new Permission("admin.settings.view.content", "View Content", "View homepage cards, knowledgebase, media", "admin", "admin.settings.view"),
        new Permission("admin.settings.view.domain", "View Domain", "View custom domain configuration", "admin", "admin.settings.view"),
        new Permission("admin.settings.view.billing", "View Billing", "View billing, subscription, and payment info", "admin", "admin.settings.view"),
        new Permission("admin.settings.view.migration", "View Migration", "View import/export data configuration", "admin", "admin.settings.view"),
        new Permission("admin.settings.view.storage", "View Storage", "View storage configuration", "admin", "admin.settings.view"),
        new Permission("admin.settings.modify", "Modify Settings", "Full control over system settings (includes all sub-permissions)", "admin"),
        new Permission("admin.settings.modify.punishments", "Modify Punishments Config", "Create/edit/delete punishment types", "admin",
            "admin.settings.modify"),
        new Permission("admin.settings.modify.content", "Modify Content", "Edit homepage cards, knowledgebase, media", "admin", "admin.settings.modify"),
        new Permission("admin.settings.modify.domain", "Modify Domain", "Change custom domain configuration", "admin", "admin.settings.modify"),
        new Permission("admin.settings.modify.billing", "Modify Billing", "Update subscription and payment methods", "admin", "admin.settings.modify"),
        new Permission("admin.settings.modify.migration", "Modify Migration", "Import/export data between platforms", "admin", "admin.settings.modify"),
        new Permission("admin.settings.modify.storage", "Modify Storage", "Configure storage backends and limits", "admin", "admin.settings.modify"),
        new Permission("admin.staff.manage", "Manage Staff", "Full staff management (includes all sub-permissions)", "admin"),
        new Permission("admin.staff.manage.members", "Manage Members", "Invite, remove, and reassign staff", "admin", "admin.staff.manage"),
        new Permission("admin.staff.manage.roles", "Manage Roles", "Create/edit/delete roles and permissions", "admin", "admin.staff.manage"),
        new Permission("admin.audit.view", "View Audit", "Full audit access (includes all sub-permissions)", "admin"),
        new Permission("admin.audit.view.dashboard", "View Dashboard", "View dashboard statistics", "admin", "admin.audit.view"),
        new Permission("admin.audit.view.analytics", "View Analytics", "View player and ticket analytics", "admin", "admin.audit.view"),
        new Permission("admin.audit.view.logs", "View Logs", "View audit trail of staff actions", "admin", "admin.audit.view"),

        // Punishment permissions
        new Permission("punishment.view", "View Punishments", "View player profiles, punishments, and linked accounts", "punishment"),
        new Permission("punishment.modify", "Modify Punishments", "Full control over existing punishments (includes all sub-permissions)", "punishment"),
        new Permission("punishment.modify.pardon", "Pardon Punishments", "Pardon punishments and clear associated points", "punishment", "punishment.modify"),
        new Permission("punishment.modify.duration", "Modify Duration", "Change punishment duration", "punishment", "punishment.modify"),
        new Permission("punishment.modify.note", "Add Notes", "Add staff notes to punishments", "punishment", "punishment.modify"),
        new Permission("punishment.modify.evidence", "Manage Evidence", "Add and view evidence on punishments", "punishment", "punishment.modify"),
        new Permission("punishment.modify.options", "Toggle Options", "Toggle alt-blocking and stat-wipe options", "punishment", "punishment.modify"),

        // Staff tool permissions
        new Permission("staff.chat.toggle", "Toggle Chat", "Toggle server chat on/off", "staff"),
        new Permission("staff.chat.clear", "Clear Chat", "Clear server chat", "staff"),
        new Permission("staff.chat.slow", "Slow Chat", "Set slow mode on server chat", "staff"),
        new Permission("staff.maintenance", "Maintenance Mode", "Toggle server maintenance mode", "staff"),
        new Permission("staff.modactions", "Moderation Actions", "Staff mode, vanish, freeze, and target players", "staff"),
        new Permission("staff.intercept", "Intercept Chat", "Intercept and view all network chat", "staff"),
        new Permission("staff.chatlogs", "Chat Logs", "View player chat history", "staff"),
        new Permission("staff.commandlogs", "Command Logs", "View player command history", "staff"),

        // Ticket permissions
        new Permission("ticket.view.all", "View All Tickets", "View all tickets (includes all sub-permissions)", "ticket"),
        new Permission("ticket.view.all.notes", "View Staff Notes", "View internal staff notes on tickets", "ticket", "ticket.view.all"),
        new Permission("ticket.reply.all", "Reply to All Tickets", "Reply to all ticket types (includes all sub-permissions)", "ticket"),
        new Permission("ticket.reply.all.notes", "Add Staff Notes", "Add staff-only internal notes", "ticket", "ticket.reply.all"),
        new Permission("appeal.modify", "Modify Appeals", "Reply to and update appeals", "ticket"),
        new Permission("ticket.close.all", "Close/Reopen All Tickets", "Close and reopen all ticket types (includes all sub-permissions)", "ticket"),
        new Permission("ticket.close.all.lock", "Lock Tickets", "Lock tickets to prevent further replies", "ticket", "ticket.close.all"),
        new Permission("ticket.manage", "Manage Tickets", "Advanced ticket management (includes all sub-permissions)", "ticket"),
        new Permission("ticket.manage.tags", "Manage Tags", "Add and remove tags from tickets", "ticket", "ticket.manage"),
        new Permission("ticket.manage.hide", "Hide Tickets", "Hide tickets from public view", "ticket", "ticket.manage"),
        new Permission("ticket.manage.subscribe", "Manage Subscriptions", "Manage ticket notification subscriptions", "ticket", "ticket.manage"),
        new Permission("ticket.delete.all", "Delete Tickets", "Delete tickets from the system", "ticket")
    );

    private static final Map<String, String> PERMISSION_CATEGORIES = Map.of(
        "punishment", "Punishment Permissions",
        "ticket", "Ticket Permissions",
        "admin", "Administrative Permissions",
        "staff", "Staff Tool Permissions"
    );

    public List<Permission> getBasePermissions() {
        return BASE_PERMISSIONS;
    }

    public Map<String, String> getPermissionCategories() {
        return PERMISSION_CATEGORIES;
    }

    public List<String> getAllPermissionIds(Server server) {
        return getAllPermissions(server).stream().map(Permission::id).toList();
    }

    public List<Permission> getAllPermissions(Server server) {
        List<Permission> all = new ArrayList<>(BASE_PERMISSIONS);
        all.addAll(getPunishmentPermissions(server));
        return all;
    }

    public List<Permission> getPunishmentPermissions(Server server) {
        List<PunishmentType> punishmentTypes = punishmentTypeService.getPunishmentTypes(server);
        List<Permission> permissions = new ArrayList<>();

        punishmentTypes.forEach(type -> {
            String permId = punishmentApplyPermissionId(type.getName());
            permissions.add(new Permission(
                permId,
                "Apply " + type.getName(),
                "Permission to apply " + type.getName() + " punishments",
                "punishment"
            ));
        });

        return permissions;
    }

    public static String punishmentApplyPermissionId(String typeName) {
        return "punishment.apply." + typeName.toLowerCase().replace(" ", "-");
    }

    public void renamePunishmentApplyPermission(Server server, String oldName, String newName) {
        if (oldName == null || newName == null) {
            return;
        }
        String oldId = punishmentApplyPermissionId(oldName);
        String newId = punishmentApplyPermissionId(newName);
        if (oldId.equals(newId)) {
            return;
        }

        boolean changed = false;
        for (StaffRole role : staffRoleRepository.findAllOrdered(server)) {
            List<String> permissions = role.getPermissions();
            if (permissions == null || !permissions.contains(oldId)) {
                continue;
            }
            List<String> migrated = new ArrayList<>();
            for (String permission : permissions) {
                String replacement = permission.equals(oldId) ? newId : permission;
                if (!migrated.contains(replacement)) {
                    migrated.add(replacement);
                }
            }
            role.setPermissions(migrated);
            staffRoleRepository.upsertRole(server, role);
            changed = true;
        }

        if (changed) {
            permissionCache.invalidateAll();
        }
    }

    public boolean hasPermission(Server server, String roleId, String permission) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }

        String trimmedRole = roleId.trim();
        String cacheKey = server.getId() + ":" + trimmedRole + ":" + permission;
        return permissionCache.get(cacheKey, key -> computeHasPermission(server, trimmedRole, permission));
    }

    private boolean computeHasPermission(Server server, String roleId, String permission) {
        StaffRole role = staffRoleRepository.findById(server, roleId).orElse(null);
        if (role == null) {
            return false;
        }
        return RoleAuthorization.roleGrants(role, permission);
    }

    public boolean hasAnyPermissionWithPrefix(Server server, String roleId, String prefix) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }

        String trimmedRole = roleId.trim();
        String cacheKey = server.getId() + ":" + trimmedRole + ":prefix:" + prefix;
        return permissionCache.get(cacheKey, key -> computeHasPermissionWithPrefix(server, trimmedRole, prefix));
    }

    private boolean computeHasPermissionWithPrefix(Server server, String roleId, String prefix) {
        StaffRole role = staffRoleRepository.findById(server, roleId).orElse(null);
        if (role == null) {
            return false;
        }
        if (RoleAuthorization.isSuperAdminRole(role)) {
            return true;
        }
        return role.getPermissions().stream().anyMatch(p -> p.startsWith(prefix));
    }

    public void evictPermissionCache() {
        permissionCache.invalidateAll();
    }

    public Optional<StaffRole> getRoleByName(Server server, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Optional.empty();
        }

        List<StaffRole> matches = staffRoleRepository.findAllByName(server, roleName.trim());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new ValidationException("Ambiguous role name '" + roleName.trim() + "' - rename roles to be unique");
        }
        return Optional.of(matches.get(0));
    }

    public Optional<StaffRole> getRoleById(Server server, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return Optional.empty();
        }

        return staffRoleRepository.findById(server, roleId.trim());
    }

    public Map<String, StaffRole> getRolesByIds(Server server, Collection<String> roleIds) {
        Set<String> ids = roleIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staffRoleRepository.findByIds(server, ids).stream()
            .collect(Collectors.toMap(StaffRole::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public Map<String, String> resolveRoleNames(Server server, Collection<String> roleIds) {
        Map<String, String> names = new LinkedHashMap<>();
        getRolesByIds(server, roleIds).forEach((id, role) -> names.put(id, role.getName()));
        return names;
    }

    public String resolveRoleName(Server server, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return "";
        }
        return getRoleById(server, roleId).map(StaffRole::getName).orElse(roleId);
    }

    public boolean isSuperAdmin(Server server, String staffEmail) {
        return RoleAuthorization.isSuperAdminEmail(server, staffEmail);
    }

    public boolean isAuthorizedEmail(Server server, String email) {
        if (isSuperAdmin(server, email)) {
            return true;
        }
        return staffRepository.findByEmailIgnoreCase(server, email).isPresent();
    }
}
