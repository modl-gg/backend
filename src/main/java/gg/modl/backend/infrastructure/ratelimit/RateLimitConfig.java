package gg.modl.backend.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RateLimitConfig {

    private final Map<RateLimitTier, Cache<String, Bucket>> buckets = new ConcurrentHashMap<>();
    private static final int MAX_BUCKETS_PER_TIER = 50_000;
    private static final Set<String> HEAVY_PANEL_WRITE_PATTERNS = Set.of(
        "/staff/invite", "/settings", "/find-linked"
    );
    private static final Set<String> HEAVY_PANEL_ANY_PATTERNS = Set.of(
        "/dashboard/metrics", "/dashboard/activity",
        "/dashboard/recent-punishments", "/dashboard/recent-tickets",
        "/ticket-subscriptions/assigned-updates", "/ticket-subscriptions/updates"
    );
    private static final Set<String> HEAVY_PUBLIC_POST_PATTERNS = Set.of(
        "/appeals", "/tickets"
    );
    private static final List<PathRule> PREFIX_RULES = List.of(
        new PathRule("/v1/webhooks/", RateLimitTier.WEBHOOK),
        new PathRule("/v1/replay-lite/", RateLimitTier.REPLAY_LITE_UPLOAD),
        new PathRule("/v1/minecraft/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule("/v2/minecraft/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule("/v3/minecraft/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule("/v1/admin/auth/session", RateLimitTier.ADMIN_SESSION),
        new PathRule("/v1/admin/auth/", RateLimitTier.ADMIN_AUTH),
        new PathRule("/v1/admin/beta-testers", RateLimitTier.ADMIN_BETA),
        new PathRule("/v1/admin/", RateLimitTier.ADMIN_STANDARD)
    );
    // Login is matched by EXACT path (not prefix) so the high-capacity login tier can never leak
    // to other /players/* routes or future nested paths.
    private static final Map<String, RateLimitTier> EXACT_PATH_RULES = Map.of(
        "/v1/minecraft/players/login", RateLimitTier.MINECRAFT_LOGIN,
        "/v3/minecraft/players/login", RateLimitTier.MINECRAFT_LOGIN
    );

    public Bucket resolveBucket(String clientKey, RateLimitTier tier) {
        return buckets
            .computeIfAbsent(tier, t -> Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS_PER_TIER)
                .<String, Bucket>build())
            .get(clientKey, k -> createBucket(tier));
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

        RateLimitTier exact = EXACT_PATH_RULES.get(path);
        if (exact != null) {
            return exact;
        }

        for (PathRule rule : PREFIX_RULES) {
            if (path.startsWith(rule.prefix())) {
                return rule.tier();
            }
        }

        if (path.startsWith("/v1/panel/auth/")) {
            boolean sendCode = path.equals("/v1/panel/auth/send-email-code")
                               || path.equals("/v1/panel/auth/email/send-code");
            return sendCode ? RateLimitTier.AUTH_SEND_CODE : RateLimitTier.AUTH;
        }

        if (path.startsWith("/v1/panel/migration/")) {
            return path.equals("/v1/panel/migration/status") && "GET".equalsIgnoreCase(method)
                   ? RateLimitTier.MIGRATION_STATUS : RateLimitTier.MIGRATION;
        }

        if (path.startsWith("/v1/panel/")) {
            return resolvePanelTier(path, method);
        }

        if (path.startsWith("/v1/public/")) {
            return resolvePublicTier(path, method);
        }

        return RateLimitTier.PANEL_STANDARD;
    }

    private RateLimitTier resolvePanelTier(String path, String method) {
        if (path.contains("/audit/") || path.contains("/analytics/")) {
            return RateLimitTier.PANEL_AUDIT;
        }
        if (isHeavyOperation(path, method, HEAVY_PANEL_WRITE_PATTERNS, HEAVY_PANEL_ANY_PATTERNS)) {
            return RateLimitTier.PANEL_HEAVY;
        }
        return RateLimitTier.PANEL_STANDARD;
    }

    private RateLimitTier resolvePublicTier(String path, String method) {
        if (path.startsWith("/v1/public/replay-lite/") && isWriteMethod(method)) {
            return RateLimitTier.REPLAY_LITE_LABEL;
        }
        if (path.startsWith("/v1/public/media/") && isWriteMethod(method)) {
            return RateLimitTier.PUBLIC_MEDIA_UPLOAD;
        }
        if (path.startsWith("/v1/public/appeals") && isWriteMethod(method)
            && (path.contains("/verify") || path.contains("/request-verification"))) {
            return RateLimitTier.PUBLIC_TICKET_VERIFY;
        }
        if (path.startsWith("/v1/public/tickets") && isWriteMethod(method)) {
            if (path.contains("/verify") || path.contains("/request-verification")) {
                return RateLimitTier.PUBLIC_TICKET_VERIFY;
            }
            if (path.equals("/v1/public/tickets") || path.equals("/v1/public/tickets/unfinished")) {
                return RateLimitTier.PUBLIC_TICKET_CREATE;
            }
            return RateLimitTier.PUBLIC_TICKET_INTERACT;
        }
        if (isHeavyOperation(path, method, HEAVY_PUBLIC_POST_PATTERNS, Set.of())) {
            return RateLimitTier.PUBLIC_HEAVY;
        }
        return RateLimitTier.PUBLIC_STANDARD;
    }

    private boolean isHeavyOperation(String path, String method, Set<String> writePatterns, Set<String> anyMethodPatterns) {
        if (isWriteMethod(method)) {
            if (path.contains("/tickets") && !path.contains("/replies")) {
                return true;
            }
            for (String pattern : writePatterns) {
                if (path.contains(pattern)) {
                    return true;
                }
            }
        }
        for (String pattern : anyMethodPatterns) {
            if (path.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
    }

    public enum RateLimitTier {
        MINECRAFT_LOGIN(10000, Duration.ofMinutes(1)),
        MINECRAFT_STANDARD(1000, Duration.ofMinutes(1)),
        PANEL_STANDARD(100, Duration.ofMinutes(1)),
        PANEL_HEAVY(20, Duration.ofMinutes(1)),
        PANEL_AUDIT(200, Duration.ofMinutes(1)),
        PUBLIC_STANDARD(60, Duration.ofMinutes(1)),
        PUBLIC_HEAVY(10, Duration.ofMinutes(1)),
        PUBLIC_MEDIA_UPLOAD(30, Duration.ofMinutes(1)),
        PUBLIC_TICKET_CREATE(2, Duration.ofMinutes(1)),
        PUBLIC_TICKET_INTERACT(10, Duration.ofMinutes(1)),
        PUBLIC_TICKET_VERIFY(10, Duration.ofMinutes(1)),
        AUTH(20, Duration.ofMinutes(1)),
        AUTH_SEND_CODE(2, Duration.ofMinutes(1)),
        ADMIN_AUTH(10, Duration.ofMinutes(1)),
        ADMIN_SESSION(30, Duration.ofMinutes(1)),
        ADMIN_STANDARD(50, Duration.ofMinutes(1)),
        ADMIN_BETA(15, Duration.ofMinutes(1)),
        WEBHOOK(50, Duration.ofMinutes(1)),
        REPLAY_LITE_UPLOAD(20, Duration.ofMinutes(1)),
        REPLAY_LITE_LABEL(20, Duration.ofMinutes(1)),
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

    private record PathRule(String prefix, RateLimitTier tier) {}
}
