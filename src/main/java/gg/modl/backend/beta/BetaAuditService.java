package gg.modl.backend.beta;

import gg.modl.backend.beta.data.BetaAudit;
import gg.modl.backend.database.mongo.repository.BetaAuditMongoRepository;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BetaAuditService {
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 50;

    private final BetaAuditMongoRepository auditRepository;

    public void record(BetaAuditAction action, String serverId, String adminEmail, String details) {
        BetaAudit audit = BetaAudit.builder()
            .action(action.name())
            .serverId(serverId)
            .adminEmail(adminEmail)
            .timestamp(new Date())
            .details(details)
            .build();
        auditRepository.saveEntity(audit);
    }

    public List<BetaAudit> findRecent(String serverId, int limit) {
        int boundedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return auditRepository.findRecentForServer(serverId, boundedLimit);
    }
}
