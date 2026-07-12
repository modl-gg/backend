package gg.modl.backend.log.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.service.StaffLookupCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PanelActionAuditor {
    private final LogService logService;
    private final StaffLookupCache staffLookupCache;

    public void recordStaffAction(Server server, String actorEmail, String description) {
        logService.recordStaffAction(server, resolveActor(server, actorEmail), description);
    }

    public void recordModerationAction(Server server, String actorEmail, String description) {
        logService.recordModerationAction(server, resolveActor(server, actorEmail), description);
    }

    private String resolveActor(Server server, String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return "Unknown";
        }
        return staffLookupCache.findByEmail(server, actorEmail)
            .map(Staff::getUsername)
            .filter(username -> username != null && !username.isBlank())
            .orElse(actorEmail);
    }
}
