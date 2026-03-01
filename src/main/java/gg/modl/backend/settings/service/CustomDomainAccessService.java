package gg.modl.backend.settings.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import org.springframework.stereotype.Service;

@Service
public class CustomDomainAccessService {

    public boolean canManageCustomDomain(Server server) {
        return server.getPlan() == ServerPlan.PREMIUM || Boolean.TRUE.equals(server.getCustomDomainGrandfathered());
    }
}
