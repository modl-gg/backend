package gg.modl.backend.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitConfig {

    private final Map<String, Map<String, Bucket>> buckets = new ConcurrentHashMap<>();

    public enum RateLimitTier {
        MINECRAFT_LOGIN(10000, Duration.ofMinutes(1)),
        MINECRAFT_STANDARD(1000, Duration.ofMinutes(1)),
        PANEL_STANDARD(100, Duration.ofMinutes(1)),
        PANEL_HEAVY(20, Duration.ofMinutes(1)),
        PUBLIC_STANDARD(60, Duration.ofMinutes(1)),
        PUBLIC_HEAVY(10, Duration.ofMinutes(1)),
        AUTH(20, Duration.ofMinutes(1)),
        ADMIN_AUTH(10, Duration.ofMinutes(1)),
        ADMIN_STANDARD(50, Duration.ofMinutes(1)),
        MIGRATION(5, Duration.ofHours(1)),
        MIGRATION_STATUS(60, Duration.ofMinutes(1));

        private final int capacity;
        private final Duration refillDuration;

        RateLimitTier(int capacity, Duration refillDuration) {
            this.capacity = capacity;
            this.refillDuration = refillDuration;
        }

        public int getCapacity() {
            return capacity;
        }

        public Duration getRefillDuration() {
            return refillDuration;
        }
    }

    public Bucket resolveBucket(String clientKey, RateLimitTier tier) {
        String tierName = tier.name();
        return buckets
                .computeIfAbsent(tierName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(clientKey, k -> createBucket(tier));
    }

    private Bucket createBucket(RateLimitTier tier) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(tier.getCapacity())
                .refillGreedy(tier.getCapacity(), tier.getRefillDuration())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public RateLimitTier getTierForPath(String path, String method) {
        if (path == null) {
            return RateLimitTier.PANEL_STANDARD;
        }

        if (path.startsWith("/v1/minecraft/login") || path.startsWith("/v1/minecraft/player/login")) {
            return RateLimitTier.MINECRAFT_LOGIN;
        }

        if (path.startsWith("/v1/minecraft/")) {
            return RateLimitTier.MINECRAFT_STANDARD;
        }

        if (path.startsWith("/v1/panel/auth/")) {
            return RateLimitTier.AUTH;
        }

        if (path.startsWith("/v1/admin/auth/")) {
            return RateLimitTier.ADMIN_AUTH;
        }

        if (path.startsWith("/v1/admin/")) {
            return RateLimitTier.ADMIN_STANDARD;
        }

        if (path.startsWith("/v1/panel/migration/")) {
            if (path.equals("/v1/panel/migration/status") && "GET".equalsIgnoreCase(method)) {
                return RateLimitTier.MIGRATION_STATUS;
            }
            return RateLimitTier.MIGRATION;
        }

        if (path.startsWith("/v1/panel/")) {
            if (isHeavyPanelOperation(path, method)) {
                return RateLimitTier.PANEL_HEAVY;
            }
            return RateLimitTier.PANEL_STANDARD;
        }

        if (path.startsWith("/v1/public/")) {
            if (isHeavyPublicOperation(path, method)) {
                return RateLimitTier.PUBLIC_HEAVY;
            }
            return RateLimitTier.PUBLIC_STANDARD;
        }

        return RateLimitTier.PANEL_STANDARD;
    }

    private boolean isHeavyPanelOperation(String path, String method) {
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (path.contains("/tickets") && !path.contains("/replies")) {
                return true;
            }
            if (path.contains("/staff/invite")) {
                return true;
            }
            if (path.contains("/settings")) {
                return true;
            }
        }

        if (path.contains("/dashboard/metrics") || path.contains("/dashboard/activity")) {
            return true;
        }

        return false;
    }

    private boolean isHeavyPublicOperation(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            if (path.contains("/appeals")) {
                return true;
            }
            if (path.contains("/tickets")) {
                return true;
            }
        }
        return false;
    }
}
