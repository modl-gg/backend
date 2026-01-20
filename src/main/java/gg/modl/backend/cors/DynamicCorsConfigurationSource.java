package gg.modl.backend.cors;

import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {
    private final ServerService serverService;

    @Value("${modl.cors.system-origins:}")
    private String systemOrigins;

    @Value("${modl.domain:modl.gg}")
    private String appDomain;

    private final ConcurrentHashMap<String, CachedOrigin> originCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return null;
        }

        if (!isOriginAllowed(origin)) {
            return null;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(origin);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Server-Domain", "X-API-Key"));
        config.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-RateLimit-Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    private boolean isOriginAllowed(String origin) {
        CachedOrigin cached = originCache.get(origin);
        if (cached != null && !cached.isExpired()) {
            return cached.allowed;
        }

        boolean allowed = checkOriginAllowed(origin);
        originCache.put(origin, new CachedOrigin(allowed, System.currentTimeMillis() + CACHE_TTL_MS));
        return allowed;
    }

    private boolean checkOriginAllowed(String origin) {
        if (isSystemOrigin(origin)) {
            return true;
        }

        String host = extractHost(origin);
        if (host == null) {
            return false;
        }

        if (isSubdomainOfAppDomain(host)) {
            return true;
        }

        Server server = serverService.getServerFromDomain(host);
        return server != null;
    }

    private boolean isSystemOrigin(String origin) {
        if (systemOrigins == null || systemOrigins.isBlank()) {
            return false;
        }
        Set<String> origins = Set.copyOf(Arrays.asList(systemOrigins.split(",")));
        return origins.contains(origin);
    }

    private boolean isSubdomainOfAppDomain(String host) {
        return host.endsWith("." + appDomain);
    }

    private String extractHost(String origin) {
        try {
            URI uri = URI.create(origin);
            return uri.getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void invalidateCache(String domain) {
        originCache.entrySet().removeIf(entry -> {
            String host = extractHost(entry.getKey());
            return domain.equals(host);
        });
    }

    public void invalidateCacheForOrigin(String origin) {
        originCache.remove(origin);
    }

    private record CachedOrigin(boolean allowed, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
