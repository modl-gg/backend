package gg.modl.backend.staff.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.auth.WebAuthnService;
import gg.modl.backend.auth.session.SessionService;
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
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.settings.data.SupportedLanguages;
import gg.modl.backend.settings.service.GeneralSettingsService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final RoleAuthorization roleAuthorization;
    private final ServerTimestampService serverTimestampService;
    private final WebAuthnService webAuthnService;
    private final SessionService sessionService;
    private final GeneralSettingsService generalSettingsService;

    private static final String SUPER_ADMIN_USERNAME = "Admin";

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
        roleIds.add(RoleAuthorization.SUPER_ADMIN_ROLE_ID);
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

        if (!superAdminFound && adminEmail != null) {
            result.add(0, toStaffResponse(buildSuperAdminStaff(server, adminEmail), "Active",
                fallbackRoleName(roleNamesById, RoleAuthorization.SUPER_ADMIN_ROLE_ID)));
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

    public long countStaffIncludingSuperAdmin(Server server) {
        long staffCount = staffRepository.countAll(server);
        String adminEmail = server.getAdminEmail();
        if (adminEmail != null && !staffRepository.existsByEmailEqualsIgnoreCase(server, adminEmail)) {
            staffCount++;
        }
        return staffCount;
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

    public StaffResponse createStaff(Server server, CreateStaffRequest request, RoleAuthorization.PerformerAuthority performer) {
        String email = EmailAddressUtil.normalizeIfValid(request.email());
        if (email == null) {
            throw new ValidationException("A valid email address is required");
        }

        if (staffRepository.existsByEmailOrUsername(server, email, request.username())) {
            throw new ConflictException("Staff member with this email or username already exists");
        }

        String requestedRole = request.role() != null ? request.role() : "Helper";
        StaffRole grantedRole = roleAuthorization.assertGrantableRole(server, performer, requestedRole);

        Staff staff = Staff.builder()
            .email(email)
            .username(request.username())
            .roleId(grantedRole.getId())
            .language(generalSettingsService.getGeneralSettings(server).getDefaultLanguage())
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

    public boolean deleteStaff(Server server, String id, RoleAuthorization.PerformerAuthority performer) {
        if (invitationRepository.deleteById(server, id)) {
            return true;
        }

        Staff staffToRemove = staffRepository.findById(server, id).orElse(null);

        if (staffToRemove == null) {
            return false;
        }

        roleAuthorization.assertCanActOnStaff(server, performer, staffToRemove);

        staffRepository.deleteById(server, id);
        evictStaffByEmailCache(server, staffToRemove.getEmail());
        webAuthnService.deleteCredentialsForEmail(server, staffToRemove.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public Optional<StaffResponse> updateStaffRole(Server server, String id, String newRole, RoleAuthorization.PerformerAuthority performer) {
        Staff staff = staffRepository.findById(server, id).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        roleAuthorization.assertCanActOnStaff(server, performer, staff);

        StaffRole validatedRole = roleAuthorization.assertGrantableRole(server, performer, newRole);
        staff.setRoleId(validatedRole.getId());
        staff.setUpdatedAt(new Date());
        Staff saved = staffRepository.saveEntity(server, staff);
        evictStaffByEmailCache(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);

        return Optional.of(toStaffResponse(server, saved, "Active"));
    }

    public List<MinecraftStaffSummaryResponse> getMinecraftStaffSummary(Server server) {
        List<Staff> allStaff = staffRepository.findAll(server);

        PlayerStaffData playerStaffData = loadPlayerStaffData(server, allStaff);
        Map<String, Integer> punishmentCounts = loadPunishmentCounts(server);
        Map<String, StaffRole> rolesById = loadRolesByStaffRoleId(server, allStaff);

        return allStaff.stream()
            .map(staff -> {
                int punishmentsIssuedCount = 0;
                Set<String> keys = new LinkedHashSet<>();
                if (staff.getId() != null) keys.add(staff.getId());
                if (staff.getAssignedMinecraftUsername() != null) keys.add(staff.getAssignedMinecraftUsername());
                if (staff.getUsername() != null) keys.add(staff.getUsername());
                for (String k : keys) {
                    punishmentsIssuedCount += punishmentCounts.getOrDefault(k, 0);
                }

                String assignedUuid = staff.getAssignedMinecraftUuid();
                String roleId = RoleAuthorization.effectiveRoleId(server, staff);
                return new MinecraftStaffSummaryResponse(
                    staff.getId(),
                    staff.getUsername(),
                    staff.getEmail(),
                    roleNameOrFallback(rolesById, roleId),
                    assignedUuid,
                    staff.getAssignedMinecraftUsername(),
                    rolePermissions(rolesById, roleId),
                    staff.getLastSeen(),
                    playerStaffData.playtimeMsFor(assignedUuid),
                    playerStaffData.lastServerFor(assignedUuid),
                    punishmentsIssuedCount,
                    staff.getCreatedAt(),
                    staff.getUpdatedAt()
                );
            })
            .toList();
    }

    private record PlayerStaffData(Map<String, Long> playtimeMap, Map<String, String> lastServerMap) {
        long playtimeMsFor(String minecraftUuid) {
            return minecraftUuid == null ? 0L : playtimeMap.getOrDefault(minecraftUuid, 0L);
        }

        String lastServerFor(String minecraftUuid) {
            return minecraftUuid == null ? null : lastServerMap.get(minecraftUuid);
        }
    }

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
            .map(staff -> {
                String roleId = RoleAuthorization.effectiveRoleId(server, staff);
                return new MinecraftStaffPermissionsResponse(
                    staff.getAssignedMinecraftUuid(),
                    staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "",
                    staff.getUsername() != null ? staff.getUsername() : "",
                    staff.getId(),
                    roleNameOrFallback(rolesById, roleId),
                    rolePermissions(rolesById, roleId),
                    staff.getEmail() != null ? staff.getEmail() : ""
                );
            })
            .toList();
    }

    public boolean updateMinecraftStaffRole(Server server, String id, String roleName,
                                            RoleAuthorization.PerformerAuthority performer) {
        roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_MEMBERS_PERMISSION);

        Staff staff = staffRepository.findById(server, id).orElse(null);
        if (staff == null) {
            return false;
        }

        roleAuthorization.assertCanActOnStaff(server, performer, staff);
        StaffRole validatedRole = roleAuthorization.assertGrantableRole(server, performer, roleName);

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

    public Optional<StaffResponse> assignMinecraftPlayer(Server server, String email,
                                                         AssignMinecraftPlayerRequest request,
                                                         RoleAuthorization.PerformerAuthority performer) {
        roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_MEMBERS_PERMISSION);

        Staff staff = staffRepository.findByEmailIgnoreCase(server, email)
            .orElseGet(() -> findUnsavedSuperAdmin(server, email));

        if (staff == null) {
            return Optional.empty();
        }

        roleAuthorization.assertCanAssignMinecraftPlayer(server, performer, staff);

        if ((request.minecraftUuid() == null || request.minecraftUuid().isEmpty()) &&
            (request.minecraftUsername() == null || request.minecraftUsername().isEmpty())) {
            if (staff.getId() == null) {
                return Optional.of(toStaffResponse(server, staff, "Active"));
            }
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

    private Staff buildSuperAdminStaff(Server server, String adminEmail) {
        return Staff.builder()
            .email(EmailAddressUtil.normalize(adminEmail))
            .username(SUPER_ADMIN_USERNAME)
            .roleId(RoleAuthorization.SUPER_ADMIN_ROLE_ID)
            .createdAt(server.getCreatedAt())
            .updatedAt(new Date())
            .build();
    }

    private Staff findUnsavedSuperAdmin(Server server, String email) {
        String adminEmail = server.getAdminEmail();
        if (adminEmail == null || !adminEmail.equalsIgnoreCase(email)) {
            return null;
        }
        return buildSuperAdminStaff(server, adminEmail);
    }

    public void offboardPreviousAdminEmail(Server server, String email) {
        if (email == null) {
            return;
        }
        staffRepository.findByEmailIgnoreCase(server, email)
            .filter(staff -> RoleAuthorization.SUPER_ADMIN_ROLE_ID.equals(staff.getRoleId()))
            .ifPresent(staff -> {
                staffRepository.deleteById(server, staff.getId());
                serverTimestampService.updateStaffPermissionsTimestamp(server);
            });
        evictStaffByEmailCache(server, email);
        webAuthnService.deleteCredentialsForEmail(server, email);
        sessionService.invalidateAllSessionsForEmail(server, email);
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

        if (SupportedLanguages.isSupported(newLanguage)) {
            staff.setLanguage(newLanguage);
            hasChanges = true;
        }

        if (newDateFormat != null && List.of(Staff.DEFAULT_DATE_FORMAT, "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD").contains(newDateFormat)) {
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
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
