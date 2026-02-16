package gg.modl.backend.cors;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {
    private final ServerService serverService;

    @Value("${modl.cors.system-origins:https://modl.gg,https://admin.modl.gg,https://modl.top,https://admin.modl.top}")
    private String systemOrigins;

    @Value("${modl.cors.app-domains:modl.gg,modl.top}")
    private String appDomains;

    private static final int MAX_CACHE_SIZE = 10_000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private final Map<String, CachedOrigin> originCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedOrigin> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    private volatile Set<String> parsedSystemOrigins = Set.of();
    private volatile Set<String> parsedAppDomains = Set.of();

    @PostConstruct
    void initParsedOrigins() {
        if (systemOrigins != null && !systemOrigins.isBlank()) {
            parsedSystemOrigins = Arrays.stream(systemOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (appDomains != null && !appDomains.isBlank()) {
            parsedAppDomains = Arrays.stream(appDomains.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return null;
        }

        String path = request.getRequestURI();
        boolean privilegedPath = isPrivilegedPath(path);

        if (privilegedPath && !isSystemOrigin(origin)) {
            return null;
        }

        if (!isOriginAllowed(origin)) {
            return null;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(origin);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Server-Domain", "X-API-Key", "Cookie", "Accept", "Origin", "Authorization"));
        config.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-RateLimit-Retry-After"));
        config.setAllowCredentials(privilegedPath);
        config.setMaxAge(3600L);
        return config;
    }

    private boolean isPrivilegedPath(String path) {
        return path != null && (
                path.startsWith(RESTMappingV1.PREFIX_PANEL + "/")
                        || path.equals(RESTMappingV1.PREFIX_PANEL)
                        || path.startsWith(RESTMappingV1.PREFIX_ADMIN + "/")
                        || path.equals(RESTMappingV1.PREFIX_ADMIN)
        );
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

        if (isAppDomainOrSubdomain(host)) {
            return true;
        }

        Server server = serverService.getServerFromDomain(host);
        return server != null;
    }

    private boolean isSystemOrigin(String origin) {
        return parsedSystemOrigins.contains(origin);
    }

    private boolean isAppDomainOrSubdomain(String host) {
        return parsedAppDomains.stream()
                .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
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
