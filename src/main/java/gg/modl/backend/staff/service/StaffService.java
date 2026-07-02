package gg.modl.backend.staff.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.service.PlayerDataUtils;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.request.CreateStaffRequest;
import gg.modl.backend.staff.dto.request.UpdateStaffRequest;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffPermissionsResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService {
    private final InvitationMongoRepository invitationRepository;
    private final StaffMongoRepository staffRepository;
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final PlayerService playerService;
    private final PermissionService permissionService;
    private final ServerTimestampService serverTimestampService;
    private final WebAuthnService webAuthnService;

    private static final String SUPER_ADMIN_ROLE_ID = "super-admin";

    // Resolved performer identity for the API-key minecraft path (no authenticated staff session).
    // A null roleId / isSuperAdmin == false signals "no trustworthy identity" (safe-degrade).
    public record MinecraftPerformer(String roleId, boolean isSuperAdmin) {}

    public MinecraftPerformer resolveMinecraftPerformer(Server server, String actingStaffId) {
        if (actingStaffId == null || actingStaffId.isBlank()) {
            return new MinecraftPerformer(null, false);
        }
        Staff staff = staffRepository.findById(server, actingStaffId).orElse(null);
        if (staff == null) {
            return new MinecraftPerformer(null, false);
        }
        boolean superAdmin = server.getAdminEmail() != null
            && server.getAdminEmail().equalsIgnoreCase(staff.getEmail());
        return new MinecraftPerformer(staff.getRoleId(), superAdmin);
    }

    private final Cache<String, Optional<Staff>> staffByEmailCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(2))
        .build();

    public List<StaffResponse> getAllStaff(Server server) {
        List<Staff> staffMembers = staffRepository.findAll(server);
        List<Invitation> pendingInvitations = invitationRepository.findActiveInvitations(server, new Date());

        Set<String> roleIds = new HashSet<>();
        staffMembers.forEach(staff -> roleIds.add(staff.getRoleId()));
        pendingInvitations.forEach(invitation -> roleIds.add(invitation.getRoleId()));
        roleIds.add(SUPER_ADMIN_ROLE_ID);
        Map<String, String> roleNamesById = permissionService.resolveRoleNames(server, roleIds);

        List<StaffResponse> result = new ArrayList<>();

        // Check if the Super Admin already has a staff record
        String adminEmail = server.getAdminEmail();
        boolean superAdminFound = false;

        for (Staff staff : staffMembers) {
            result.add(toStaffResponse(staff, "Active", fallbackRoleName(roleNamesById, staff.getRoleId())));
            if (adminEmail != null && adminEmail.equalsIgnoreCase(staff.getEmail())) {
                superAdminFound = true;
            }
        }

        // Expose the owner in listings without creating a privileged staff document implicitly.
        if (!superAdminFound && adminEmail != null) {
            Staff superAdmin = Staff.builder()
                .email(adminEmail)
                .username("Admin")
                .roleId(SUPER_ADMIN_ROLE_ID)
                .createdAt(server.getCreatedAt())
                .updatedAt(new Date())
                .build();
            result.add(0, toStaffResponse(superAdmin, "Active", fallbackRoleName(roleNamesById, SUPER_ADMIN_ROLE_ID)));
        }

        for (Invitation invitation : pendingInvitations) {
            result.add(new StaffResponse(
                invitation.getId(),
                invitation.getEmail(),
                null,
                fallbackRoleName(roleNamesById, invitation.getRoleId()),
                "Pending Invitation",
                null,
                null,
                invitation.getCreatedAt()
            ));
        }

        return result;
    }

    private StaffResponse toStaffResponse(Staff staff, String status, String roleName) {
        return new StaffResponse(
            staff.getId(),
            staff.getEmail(),
            staff.getUsername(),
            roleName,
            status,
            staff.getAssignedMinecraftUuid(),
            staff.getAssignedMinecraftUsername(),
            staff.getCreatedAt()
        );
    }

    private StaffResponse toStaffResponse(Server server, Staff staff, String status) {
        return toStaffResponse(staff, status, permissionService.resolveRoleName(server, staff.getRoleId()));
    }

    private static String fallbackRoleName(Map<String, String> roleNamesById, String roleId) {
        if (roleId == null) {
            return "";
        }
        return roleNamesById.getOrDefault(roleId, roleId);
    }

    public Optional<StaffResponse> getStaffByUsername(Server server, String username) {
        return staffRepository.findByUsername(server, username).map(s -> toStaffResponse(server, s, "Active"));
    }

    public boolean checkUsernameExists(Server server, String username) {
        return staffRepository.existsByUsername(server, username);
    }

    public StaffResponse createStaff(Server server, CreateStaffRequest request, String performerEmail, String performerRole) {
        // Canonicalize the email so it stores in the same lowercase form every lookup queries
        // (read paths normalize); otherwise mixed-case storage is unreachable and duplicable.
        String email = EmailAddressUtil.normalizeIfValid(request.email());
        if (email == null) {
            throw new ValidationException("A valid email address is required");
        }

        if (staffRepository.existsByEmailOrUsername(server, email, request.username())) {
            throw new ConflictException("Staff member with this email or username already exists");
        }

        String requestedRole = request.role() != null ? request.role() : "Helper";
        StaffRole grantedRole = validateGrantableRole(server, requestedRole, performerRole);

        Staff staff = Staff.builder()
            .email(email)
            .username(request.username())
            .roleId(grantedRole.getId())
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        staffRepository.saveEntity(server, staff);
        evictStaffByEmailCache(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return toStaffResponse(server, staff, "Active");
    }

    public Optional<StaffResponse> updateStaff(Server server, String username, UpdateStaffRequest request) {
        Staff staff = staffRepository.findByUsername(server, username).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        if (request.email() != null) {
            String requestedEmail = EmailAddressUtil.normalizeIfValid(request.email());
            if (requestedEmail == null) {
                throw new ValidationException("A valid email address is required");
            }
            if (!requestedEmail.equals(staff.getEmail())) {
                throw new ForbiddenException("Email changes must be confirmed through email verification in Account Settings");
            }
        }

        return Optional.of(toStaffResponse(server, staff, "Active"));
    }

    public boolean deleteStaff(Server server, String id, String removerEmail) {
        if (invitationRepository.deleteById(server, id)) {
            return true;
        }

        Staff staffToRemove = staffRepository.findById(server, id).orElse(null);

        if (staffToRemove == null) {
            return false;
        }

        if (staffToRemove.getEmail().equalsIgnoreCase(removerEmail)) {
            throw new ValidationException("You cannot remove yourself");
        }

        if (server.getAdminEmail() != null &&
            staffToRemove.getEmail().equalsIgnoreCase(server.getAdminEmail())) {
            throw new ForbiddenException("Cannot remove the server administrator");
        }

        staffRepository.deleteById(server, id);
        evictStaffByEmailCache(server, staffToRemove.getEmail());
        // Purge the removed staff's passkeys so a de-authorized email keeps no stale WebAuthn credentials.
        webAuthnService.deleteCredentialsForEmail(server, staffToRemove.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public Optional<StaffResponse> updateStaffRole(Server server, String id, String newRole, String performerEmail, String performerRole) {
        Staff staff = staffRepository.findById(server, id).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        if (server.getAdminEmail() != null &&
            staff.getEmail().equalsIgnoreCase(server.getAdminEmail())) {
            throw new ForbiddenException("Cannot change the role of the server administrator");
        }

        if (staff.getEmail().equalsIgnoreCase(performerEmail)) {
            throw new ForbiddenException("You cannot change your own role");
        }

        StaffRole validatedRole = validateGrantableRole(server, newRole, performerRole);
        staff.setRoleId(validatedRole.getId());
        staff.setUpdatedAt(new Date());
        Staff saved = staffRepository.saveEntity(server, staff);
        evictStaffByEmailCache(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return Optional.of(toStaffResponse(server, saved, "Active"));
    }

    // targetRoleName is the requested role (clients send role names); performerRoleId is the acting staff's stored role id.
    private StaffRole validateGrantableRole(Server server, String targetRoleName, String performerRoleId) {
        StaffRole targetRole = permissionService.getRoleByName(server, targetRoleName)
            .orElseThrow(() -> new ValidationException("Unknown staff role"));
        if (SUPER_ADMIN_ROLE_ID.equals(targetRole.getId())) {
            throw new ForbiddenException("You do not have authority to grant this role");
        }
        if (performerRoleId == null || performerRoleId.isBlank()) {
            throw new ForbiddenException("You do not have authority to grant staff roles");
        }
        if (SUPER_ADMIN_ROLE_ID.equals(performerRoleId)) {
            return targetRole;
        }
        StaffRole performerRole = permissionService.getRoleById(server, performerRoleId)
            .orElseThrow(() -> new ForbiddenException("You do not have authority to grant staff roles"));
        if (performerRole.getOrder() >= targetRole.getOrder()) {
            throw new ForbiddenException("You do not have authority to grant this role");
        }
        return targetRole;
    }

    public List<MinecraftStaffSummaryResponse> getMinecraftStaffSummary(Server server) {
        List<Staff> allStaff = staffRepository.findAll(server);

        PlayerStaffData playerStaffData = loadPlayerStaffData(server, allStaff);
        Map<String, Integer> punishmentCounts = loadPunishmentCounts(server);
        Map<String, StaffRole> rolesById = loadRolesByStaffRoleId(server, allStaff);

        return allStaff.stream()
            .map(staff -> {
                // Sum the effective-issuer buckets keyed by this staff's distinct identities (id for
                // panel-issued punishments; username/assignedUsername for in-game ones). LinkedHashSet
                // dedups equal keys; the buckets are otherwise disjoint, so summation never double-counts.
                int punishmentsIssuedCount = 0;
                java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                if (staff.getId() != null) keys.add(staff.getId());
                if (staff.getAssignedMinecraftUsername() != null) keys.add(staff.getAssignedMinecraftUsername());
                if (staff.getUsername() != null) keys.add(staff.getUsername());
                for (String k : keys) {
                    punishmentsIssuedCount += punishmentCounts.getOrDefault(k, 0);
                }

                return new MinecraftStaffSummaryResponse(
                    staff.getId(),
                    staff.getUsername(),
                    staff.getEmail(),
                    roleNameOrFallback(rolesById, staff.getRoleId()),
                    staff.getAssignedMinecraftUuid(),
                    staff.getAssignedMinecraftUsername(),
                    rolePermissions(rolesById, staff.getRoleId()),
                    staff.getLastSeen(),
                    playerStaffData.playtimeMap().getOrDefault(staff.getAssignedMinecraftUuid(), 0L),
                    playerStaffData.lastServerMap().get(staff.getAssignedMinecraftUuid()),
                    punishmentsIssuedCount,
                    staff.getCreatedAt(),
                    staff.getUpdatedAt()
                );
            })
            .toList();
    }

    private record PlayerStaffData(Map<String, Long> playtimeMap, Map<String, String> lastServerMap) {}

    private PlayerStaffData loadPlayerStaffData(Server server, List<Staff> allStaff) {
        List<String> assignedUuids = allStaff.stream()
            .map(Staff::getAssignedMinecraftUuid)
            .filter(uuid -> uuid != null && !uuid.isBlank())
            .distinct()
            .toList();

        if (assignedUuids.isEmpty()) {
            return new PlayerStaffData(Map.of(), Map.of());
        }

        Map<String, Long> playtimeMap = new HashMap<>();
        Map<String, String> lastServerMap = new HashMap<>();

        for (Player player : playerRepository.findByMinecraftUuids(server, assignedUuids)) {
            if (player.getMinecraftUuid() == null || player.getData() == null) {
                continue;
            }

            String uuid = player.getMinecraftUuid().toString();

            Object playtimeObj = player.getData().get("totalPlaytimeSeconds");
            if (playtimeObj instanceof Number playtimeSeconds) {
                playtimeMap.put(uuid, playtimeSeconds.longValue() * 1000L);
            }

            Object lastServerObj = player.getData().get("lastServer");
            if (lastServerObj instanceof String lastServer) {
                lastServerMap.put(uuid, lastServer);
            }
        }

        return new PlayerStaffData(playtimeMap, lastServerMap);
    }

    private Map<String, Integer> loadPunishmentCounts(Server server) {
        try {
            // Keyed by effective issuer ($ifNull(issuerId, issuerName)) so panel-issued punishments
            // (issuerName == null, issuerId set) are counted toward the issuing staff by id.
            return punishmentRepository.countPunishmentsByEffectiveIssuer(server);
        } catch (Exception e) {
            log.warn("Failed to load punishment counts for server {}", server.getDatabaseName(), e);
            return Map.of();
        }
    }

    private Map<String, StaffRole> loadRolesByStaffRoleId(Server server, List<Staff> staffMembers) {
        List<String> roleIds = staffMembers.stream()
            .map(Staff::getRoleId)
            .toList();
        return permissionService.getRolesByIds(server, roleIds);
    }

    private static String roleNameOrFallback(Map<String, StaffRole> rolesById, String roleId) {
        StaffRole role = roleId != null ? rolesById.get(roleId) : null;
        if (role != null) {
            return role.getName();
        }
        return roleId != null ? roleId : "";
    }

    private static List<String> rolePermissions(Map<String, StaffRole> rolesById, String roleId) {
        StaffRole role = roleId != null ? rolesById.get(roleId) : null;
        return role != null && role.getPermissions() != null ? role.getPermissions() : List.of();
    }

    public List<MinecraftStaffPermissionsResponse> getMinecraftStaffPermissions(Server server) {
        List<Staff> staffWithMinecraft = staffRepository.findAssignedMinecraftStaff(server);
        Map<String, StaffRole> rolesById = loadRolesByStaffRoleId(server, staffWithMinecraft);

        return staffWithMinecraft.stream()
            .map(staff -> new MinecraftStaffPermissionsResponse(
                staff.getAssignedMinecraftUuid(),
                staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "",
                staff.getUsername() != null ? staff.getUsername() : "",
                staff.getId(),
                roleNameOrFallback(rolesById, staff.getRoleId()),
                rolePermissions(rolesById, staff.getRoleId()),
                staff.getEmail() != null ? staff.getEmail() : ""
            ))
            .toList();
    }

    public boolean updateMinecraftStaffRole(Server server, String id, String roleName, String actingStaffId,
                                            String performerRoleId, boolean isSuperAdmin, boolean hasPerformerIdentity) {
        Staff staff = staffRepository.findById(server, id).orElse(null);
        if (staff == null) {
            return false;
        }

        // Always-safe protections that need no performer identity.
        if (server.getAdminEmail() != null && staff.getEmail() != null
            && staff.getEmail().equalsIgnoreCase(server.getAdminEmail())) {
            throw new ForbiddenException("Cannot change the role of the server administrator");
        }
        if (hasPerformerIdentity && actingStaffId != null && actingStaffId.equals(id)) {
            throw new ForbiddenException("You cannot change your own role");
        }

        StaffRole validatedRole;
        if (hasPerformerIdentity) {
            // Full panel-equivalent enforcement (hierarchy + grantability + super-admin block).
            String effectivePerformerRoleId = isSuperAdmin ? SUPER_ADMIN_ROLE_ID : performerRoleId;
            validatedRole = validateGrantableRole(server, roleName, effectivePerformerRoleId);
        } else {
            // Legacy/owner/absent-header degrade: keep the super-admin-grant block (validate as super-admin
            // performer so the order check is skipped) but resolve role-not-found as a 404 like before.
            validatedRole = validateGrantableRole(server, roleName, SUPER_ADMIN_ROLE_ID);
        }

        staff.setRoleId(validatedRole.getId());
        staff.setUpdatedAt(new Date());
        staffRepository.saveEntity(server, staff);
        evictStaffByEmailCache(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public boolean markStaffDisconnected(Server server, String minecraftUuid) {
        return staffRepository.updateLastSeenByAssignedMinecraftUuid(server, normalizeUuid(minecraftUuid));
    }

    public Optional<StaffResponse> assignMinecraftPlayer(Server server, String username, AssignMinecraftPlayerRequest request) {
        Staff staff = staffRepository.findByUsername(server, username).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        if ((request.minecraftUuid() == null || request.minecraftUuid().isEmpty()) &&
            (request.minecraftUsername() == null || request.minecraftUsername().isEmpty())) {
            staff.setAssignedMinecraftUuid(null);
            staff.setAssignedMinecraftUsername(null);
            staff.setUpdatedAt(new Date());
            staffRepository.saveEntity(server, staff);
            evictStaffByEmailCache(server, staff.getEmail());
            serverTimestampService.updateStaffPermissionsTimestamp(server);
            return Optional.of(toStaffResponse(server, staff, "Active"));
        }

        Player player = request.minecraftUuid() != null && !request.minecraftUuid().isEmpty()
                        ? playerRepository.findByMinecraftUuid(server, normalizeUuid(request.minecraftUuid())).orElse(null)
                        : playerService.findBestByUsername(server, request.minecraftUsername()).orElse(null);
        if (player == null) {
            throw new ResourceNotFoundException("Minecraft player not found");
        }

        Staff existingAssignment = staffRepository
            .findByAssignedMinecraftUuidExcludingId(server, player.getMinecraftUuid().toString(), staff.getId())
            .orElse(null);
        if (existingAssignment != null) {
            throw new ConflictException("This Minecraft player is already assigned to " + existingAssignment.getUsername());
        }

        String currentUsername = PlayerDataUtils.extractLatestUsername(player.getUsernames());

        staff.setAssignedMinecraftUuid(player.getMinecraftUuid().toString());
        staff.setAssignedMinecraftUsername(currentUsername);
        staff.setUpdatedAt(new Date());
        staffRepository.saveEntity(server, staff);
        evictStaffByEmailCache(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return Optional.of(toStaffResponse(server, staff, "Active"));
    }

    public List<AvailablePlayerResponse> getAvailablePlayers(Server server) {
        List<Staff> staffWithPlayers = staffRepository.findAssignedMinecraftStaff(server);

        List<String> assignedUuids = staffWithPlayers.stream()
            .map(Staff::getAssignedMinecraftUuid)
            .filter(uuid -> uuid != null && !uuid.isEmpty())
            .toList();

        List<Player> players = playerRepository.findAvailablePlayers(server, assignedUuids, 100);

        return players.stream()
            .map(player -> new AvailablePlayerResponse(
                player.getMinecraftUuid().toString(),
                PlayerDataUtils.extractLatestUsername(player.getUsernames())
            ))
            .toList();
    }

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername,
                                                         boolean createIfNotExists, String newLanguage, String newDateFormat) {
        Staff staff = staffRepository.findByEmailIgnoreCase(server, email).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        boolean hasChanges = false;
        if (newUsername != null && !newUsername.equals(staff.getUsername())) {
            if (staffRepository.existsByUsernameExcludingId(server, newUsername, staff.getId())) {
                throw new ConflictException("Username already in use");
            }
            staff.setUsername(newUsername);
            hasChanges = true;
        }

        if (newLanguage != null && List.of("en", "de", "es").contains(newLanguage)) {
            staff.setLanguage(newLanguage);
            hasChanges = true;
        }

        if (newDateFormat != null && List.of("MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD").contains(newDateFormat)) {
            staff.setDateFormat(newDateFormat);
            hasChanges = true;
        }

        if (hasChanges) {
            staff.setUpdatedAt(new Date());
            staff = staffRepository.saveEntity(server, staff);
            evictStaffByEmailCache(server, email);
        }

        return Optional.of(staff);
    }

    public boolean isStaffEmailInUse(Server server, String newEmail, String excludingCurrentEmail) {
        return staffRepository.existsByEmailIgnoreCaseExcluding(server, EmailAddressUtil.normalize(newEmail), excludingCurrentEmail);
    }

    public Optional<Staff> applyStaffEmailChange(Server server, String currentEmail, String newEmail) {
        Staff staff = staffRepository.findByEmailIgnoreCase(server, currentEmail).orElse(null);
        if (staff == null) {
            return Optional.empty();
        }

        staff.setEmail(EmailAddressUtil.normalize(newEmail));
        staff.setUpdatedAt(new Date());
        staff = staffRepository.saveEntity(server, staff);

        evictStaffByEmailCache(server, currentEmail);
        evictStaffByEmailCache(server, staff.getEmail());
        return Optional.of(staff);
    }

    public Optional<Staff> getStaffByEmail(Server server, String email) {
        String normalized = EmailAddressUtil.normalize(email);
        if (normalized == null) {
            return Optional.empty();
        }
        String cacheKey = server.getId() + ":" + normalized;
        return staffByEmailCache.get(cacheKey, key ->
            staffRepository.findByEmailIgnoreCase(server, email)
        );
    }

    public void evictStaffByEmailCache(Server server, String email) {
        String normalized = EmailAddressUtil.normalize(email);
        if (normalized == null) {
            return;
        }
        staffByEmailCache.invalidate(server.getId() + ":" + normalized);
    }

    public void evictAllStaffCaches() {
        staffByEmailCache.invalidateAll();
    }

    private static String normalizeUuid(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }
}
