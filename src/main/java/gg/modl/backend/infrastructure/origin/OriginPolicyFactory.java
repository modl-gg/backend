package gg.modl.backend.infrastructure.origin;

import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.infrastructure.util.HostExtractionUtil;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OriginPolicyFactory {
    private final ModlCorsProperties corsProperties;
    private final ModlProperties modlProperties;

    public OriginPolicy withAppDomains() {
        return new OriginPolicy(
            HostExtractionUtil.parseCommaSeparated(corsProperties.getSystemOrigins()),
            HostExtractionUtil.parseCommaSeparated(corsProperties.getAppDomains()),
            modlProperties.isDevelopmentMode()
        );
    }

    public OriginPolicy systemOriginsOnly() {
        return new OriginPolicy(
            HostExtractionUtil.parseCommaSeparated(corsProperties.getSystemOrigins()),
            Set.of(),
            modlProperties.isDevelopmentMode()
        );
    }
}
