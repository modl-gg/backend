package gg.modl.backend.infrastructure.origin;

import gg.modl.backend.infrastructure.util.HostExtractionUtil;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class OriginPolicy {
    private final Set<String> systemOrigins;
    private final Set<String> appDomains;
    private final boolean developmentMode;

    public OriginPolicy(Set<String> systemOrigins, Set<String> appDomains, boolean developmentMode) {
        this.systemOrigins = Set.copyOf(systemOrigins);
        this.appDomains = Set.copyOf(appDomains);
        this.developmentMode = developmentMode;
    }

    public boolean isSystemOrigin(@Nullable String origin) {
        return origin != null && systemOrigins.contains(origin);
    }

    public boolean isTenantOwnOrigin(@Nullable String origin, @Nullable String serverDomain) {
        String originHost = HostExtractionUtil.extractHost(origin);
        String domainHost = HostExtractionUtil.extractHost(serverDomain);
        return originHost != null && domainHost != null && originHost.equalsIgnoreCase(domainHost);
    }

    public boolean isAppDomainOrSubdomain(@Nullable String host) {
        if (host == null) {
            return false;
        }
        return appDomains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    public boolean isDevLocalhost(@Nullable String host) {
        return developmentMode && isLocalhostHost(host);
    }

    private boolean isLocalhostHost(@Nullable String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
            || "0.0.0.0".equals(normalized)
            || "::1".equals(normalized)
            || normalized.startsWith("127.");
    }
}
