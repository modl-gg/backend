package gg.modl.backend.settings.service;

import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomDomainAccessService {
    private final ServerLimitPolicy serverLimitPolicy;

    public boolean canManageCustomDomain(Server server) {
        return serverLimitPolicy.resolve(server).isCustomDomainAllowed();
    }
}
