package gg.modl.backend.server.service;

import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PanelDomainResolver {

    private final ModlProperties modlProperties;

    public String panelDomain(Server server) {
        String override = server.getCustomDomainOverride();
        if (override != null && !override.isBlank()) {
            return override;
        }
        return server.getCustomDomain() + "." + modlProperties.getDomain();
    }
}
