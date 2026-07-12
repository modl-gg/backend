package gg.modl.backend.staff.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.player.PlayerService;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.service.PlayerDataUtils;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.service.ServerTimestampService;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.AssignMinecraftPlayerRequest;
import gg.modl.backend.staff.dto.response.AvailablePlayerResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffPermissionsResponse;
import gg.modl.backend.staff.dto.response.MinecraftStaffSummaryResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
public class MinecraftStaffService {
    private final StaffMongoRepository staffRepository;
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final PlayerService playerService;
    private final PermissionService permissionService;
    private final RoleAuthorization roleAuthorization;
    private final ServerTimestampService serverTimestampService;
    private final StaffLookupCache staffLookupCache;

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
            if (player.getMinecraftUuid() == null) {
                continue;
            }

            String uuid = player.getMinecraftUuid().toString();

            Number playtimeSeconds = player.data().totalPlaytimeSeconds();
            if (playtimeSeconds != null) {
                playtimeMap.put(uuid, playtimeSeconds.longValue() * 1000L);
            }

            String lastServer = player.data().lastServer();
            if (lastServer != null) {
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
        staffLookupCache.evict(server, staff.getEmail());
        serverTimestampService.updateStaffPermissionsTimestamp(server);
        return true;
    }

    public boolean markStaffDisconnected(Server server, String minecraftUuid) {
        return staffRepository.updateLastSeenByAssignedMinecraftUuid(server, UuidUtils.normalize(minecraftUuid));
    }

    public Optional<StaffResponse> assignMinecraftPlayer(Server server, String email,
                                                         AssignMinecraftPlayerRequest request,
                                                         RoleAuthorization.PerformerAuthority performer) {
        roleAuthorization.requireStaffManage(server, performer, RoleAuthorization.MANAGE_MEMBERS_PERMISSION);

        Staff staff = staffRepository.findByEmailIgnoreCase(server, email)
            .orElseGet(() -> SuperAdminStaffSynthesizer.synthesizeIfAdminEmail(server, email));

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
            staffLookupCache.evict(server, staff.getEmail());
            serverTimestampService.updateStaffPermissionsTimestamp(server);
            return Optional.of(toStaffResponse(server, staff, "Active"));
        }

        Player player = request.minecraftUuid() != null && !request.minecraftUuid().isEmpty()
                        ? playerRepository.findByMinecraftUuid(server, UuidUtils.normalize(request.minecraftUuid())).orElse(null)
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
        staffLookupCache.evict(server, staff.getEmail());
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

    private StaffResponse toStaffResponse(Server server, Staff staff, String status) {
        return StaffResponseFactory.of(staff, status, permissionService.resolveRoleName(server, staff.getRoleId()));
    }
}
