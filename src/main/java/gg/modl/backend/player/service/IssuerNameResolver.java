package gg.modl.backend.player.service;

import gg.modl.backend.database.mongo.fields.StaffFields;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        Map<String, String> result = new HashMap<>();
        if (issuerIds == null || issuerIds.isEmpty()) {
            return result;
        }

        Query query = Query.query(Criteria.where("_id").in(issuerIds));
        query.fields().include(StaffFields.USERNAME);
        for (Staff staff : staffRepository.find(server, query)) {
            if (staff.getId() != null && staff.getUsername() != null) {
                result.put(staff.getId(), staff.getUsername());
            }
        }

        return result;
    }
}
