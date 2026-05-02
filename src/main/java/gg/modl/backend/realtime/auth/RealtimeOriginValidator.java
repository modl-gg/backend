package gg.modl.backend.realtime.auth;

import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.infrastructure.util.HostExtractionUtil;
import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeOriginValidator {
    private final ModlCorsProperties corsProperties;
    private final ModlProperties modlProperties;
    private volatile Set<String> parsedSystemOrigins = Set.of();

    @PostConstruct
    void initParsedOrigins() {
        Set<String> origins = new HashSet<>(HostExtractionUtil.parseCommaSeparated(corsProperties.getSystemOrigins()));
        if (modlProperties.isDevelopmentMode()) {
            origins.add("http://localhost:3000");
            origins.add("http://localhost:5173");
        }
        parsedSystemOrigins = origins;
    }

    public boolean isAllowedPanelOrigin(@Nullable String origin, String serverDomain) {
        if (origin == null || origin.isBlank()) {
            return false;
        }

        if (parsedSystemOrigins.contains(origin)) {
            return true;
        }

        String originHost = HostExtractionUtil.extractHost(origin);
        if (originHost == null) {
            return false;
        }

        String serverHost = HostExtractionUtil.extractHost(serverDomain);
        return serverHost != null && originHost.equalsIgnoreCase(serverHost);
    }
}
