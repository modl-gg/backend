package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class IssuerNameResolver {

    public String resolve(@Nullable String issuerId, @Nullable String issuerName, Server server, StaffMongoRepository staffRepository) {
        if (issuerId != null) {
            Staff staff = staffRepository.findById(server, issuerId).orElse(null);
            if (staff != null && staff.getUsername() != null) {
                return staff.getUsername();
            }
            return issuerName != null ? issuerName : "Unknown Staff";
        }
        if (issuerName != null) {
            return issuerName;
        }
        return "Console";
    }

    public String resolve(@Nullable String issuerId, @Nullable String issuerName, Map<String, String> resolvedMap) {
        if (issuerId != null && resolvedMap.containsKey(issuerId)) {
            return resolvedMap.get(issuerId);
        }
        if (issuerName != null) {
            return issuerName;
        }
        return issuerId != null ? "Unknown Staff" : "Console";
    }

    public Map<String, String> batchResolve(Set<String> issuerIds, Server server, StaffMongoRepository staffRepository) {
        if (issuerIds == null || issuerIds.isEmpty()) {
            return Map.of();
        }
        return staffRepository.findUsernamesByIds(server, issuerIds);
    }
}
