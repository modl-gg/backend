package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffRoleMongoRepository;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncActiveStaffService {
    private final StaffMongoRepository staffRepository;
    private final StaffRoleMongoRepository staffRoleRepository;

    public List<Map<String, Object>> getActiveStaffMembers(Server server, Map<String, String> onlinePlayerIps) {
        List<Staff> staffWithMinecraft = staffRepository.findAssignedMinecraftStaff(server);
        Map<String, StaffRole> rolesById = loadRolesById(server, staffWithMinecraft);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff staff : staffWithMinecraft) {
            StaffRole role = staff.getRoleId() != null ? rolesById.get(staff.getRoleId()) : null;
            List<String> permissions = role != null && role.getPermissions() != null ? role.getPermissions() : List.of();
            String roleName = role != null ? role.getName() : (staff.getRoleId() != null ? staff.getRoleId() : "");

            String currentIp = onlinePlayerIps.get(staff.getAssignedMinecraftUuid());
            boolean sessionValid = staff.getTwoFactorSessionExpiresAt() != null
                                   && staff.getTwoFactorSessionExpiresAt() > Instant.now().toEpochMilli()
                                   && staff.getTwoFactorSessionIp() != null
                                   && staff.getTwoFactorSessionIp().equals(currentIp);

            Map<String, Object> entry = new HashMap<>();
            entry.put("minecraftUuid", staff.getAssignedMinecraftUuid());
            entry.put("minecraftUsername", staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "");
            entry.put("staffUsername", staff.getUsername() != null ? staff.getUsername() : "");
            entry.put("staffId", staff.getId());
            entry.put("staffRole", roleName);
            entry.put("permissions", permissions);
            entry.put("email", staff.getEmail() != null ? staff.getEmail() : "");
            entry.put("twoFactorSessionValid", sessionValid);
            result.add(entry);
        }

        return result;
    }

    private Map<String, StaffRole> loadRolesById(Server server, List<Staff> staffMembers) {
        Set<String> roleIds = new HashSet<>();
        for (Staff staff : staffMembers) {
            if (staff.getRoleId() != null && !staff.getRoleId().isBlank()) {
                roleIds.add(staff.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<String, StaffRole> result = new HashMap<>();
        for (StaffRole role : staffRoleRepository.findByIds(server, roleIds)) {
            result.put(role.getId(), role);
        }
        return result;
    }
}
