package gg.modl.backend.infrastructure.cors;

import gg.modl.backend.infrastructure.config.ModlCorsProperties;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.infrastructure.origin.OriginPolicy;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.util.HostExtractionUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Component
@RequiredArgsConstructor
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {
    private final ServerService serverService;
    private final ModlCorsProperties corsProperties;
    private final ModlProperties modlProperties;
    private final Map<String, CachedOrigin> originCache = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedOrigin> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );
    private volatile OriginPolicy originPolicy = new OriginPolicy(Set.of(), Set.of(), false);
    private volatile Set<String> parsedReplayLiteOrigins = Set.of();
    private static final int MAX_CACHE_SIZE = 10_000;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            return null;
        }

        String path = request.getRequestURI();
        boolean adminPath = isAdminPath(path);

        if (adminPath && !originPolicy.isSystemOrigin(origin)) {
            return null;
        }

        if (!isOriginAllowed(path, origin)) {
            return null;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(origin);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Server-Domain", "X-API-Key", "Cookie", "Accept", "Origin", "Authorization"));
        config.setExposedHeaders(List.of(
            "X-RateLimit-Remaining",
            "X-RateLimit-Retry-After",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Retry-After-Seconds"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    private boolean isAdminPath(String path) {
        return path != null && (
            path.startsWith(RESTMappingV1.PREFIX_ADMIN + "/")
            || path.equals(RESTMappingV1.PREFIX_ADMIN)
        );
    }

    private boolean isReplayLitePath(String path) {
        return path != null && (
            path.startsWith(RESTMappingV1.PREFIX_REPLAY_LITE + "/")
            || path.startsWith(RESTMappingV1.PREFIX_PUBLIC + "/replay-lite/")
        );
    }

    private boolean isOriginAllowed(String path, String origin) {
        String cacheKey = (isReplayLitePath(path) ? "replay-lite" : "default") + ":" + origin;
        CachedOrigin cached = originCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.allowed;
        }

        boolean allowed = checkOriginAllowed(path, origin);
        originCache.put(cacheKey, new CachedOrigin(allowed, System.currentTimeMillis() + CACHE_TTL_MS));
        return allowed;
    }

    private boolean checkOriginAllowed(String path, String origin) {
        if (isReplayLitePath(path)) {
            return parsedReplayLiteOrigins.contains(origin);
        }

        if (originPolicy.isSystemOrigin(origin)) {
            return true;
        }

        String host = HostExtractionUtil.extractHost(origin);
        if (host == null) {
            return false;
        }

        if (originPolicy.isAppDomainOrSubdomain(host)) {
            return true;
        }

        Server server = serverService.getServerFromDomain(host);
        return server != null;
    }

    @PostConstruct
    void initParsedOrigins() {
        originPolicy = new OriginPolicy(
            HostExtractionUtil.parseCommaSeparated(corsProperties.getSystemOrigins()),
            HostExtractionUtil.parseCommaSeparated(corsProperties.getAppDomains()),
            modlProperties.isDevelopmentMode()
        );
        parsedReplayLiteOrigins = HostExtractionUtil.parseCommaSeparated(corsProperties.getReplayLiteOrigins());
    }

    public void invalidateCache(String domain) {
        originCache.entrySet().removeIf(entry -> {
            String host = HostExtractionUtil.extractHost(originFromCacheKey(entry.getKey()));
            return domain.equals(host);
        });
    }

    public void invalidateCacheForOrigin(String origin) {
        originCache.remove("default:" + origin);
        originCache.remove("replay-lite:" + origin);
    }

    private String originFromCacheKey(String key) {
        int separator = key.indexOf(':');
        return separator >= 0 ? key.substring(separator + 1) : key;
    }

    private record CachedOrigin(boolean allowed, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
