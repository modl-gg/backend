package gg.modl.backend.infrastructure.ratelimit;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RESTMappingV2;
import gg.modl.backend.infrastructure.rest.RESTMappingV3;
import gg.modl.backend.infrastructure.rest.RouteGroups;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitConfig {

    private final BucketPool bucketPool;

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
        new PathRule(RESTMappingV1.PREFIX_REPLAY_LITE + "/", RateLimitTier.REPLAY_LITE_UPLOAD),
        new PathRule(RESTMappingV1.PREFIX_MINECRAFT + "/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule(RESTMappingV2.PREFIX_MINECRAFT + "/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule(RESTMappingV3.PREFIX_MINECRAFT + "/", RateLimitTier.MINECRAFT_STANDARD),
        new PathRule(RESTMappingV1.ADMIN_AUTH + "/session", RateLimitTier.ADMIN_SESSION),
        new PathRule(RESTMappingV1.ADMIN_AUTH + "/", RateLimitTier.ADMIN_AUTH),
        new PathRule(RESTMappingV1.ADMIN_BETA_TESTERS, RateLimitTier.ADMIN_BETA),
        new PathRule(RESTMappingV1.PREFIX_ADMIN + "/", RateLimitTier.ADMIN_STANDARD)
    );
    private static final Map<String, RateLimitTier> EXACT_PATH_RULES = Map.of(
        RESTMappingV1.MINECRAFT_PLAYERS + "/login", RateLimitTier.MINECRAFT_LOGIN,
        RESTMappingV3.PREFIX_MINECRAFT + "/players/login", RateLimitTier.MINECRAFT_LOGIN
    );

    public Bucket resolveBucket(String clientKey, RateLimitTier tier) {
        return bucketPool.resolveBucket(tier.name(), clientKey, tier.getCapacity(), tier.getRefillDuration());
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

        if (path.startsWith(RESTMappingV1.PANEL_AUTH + "/")) {
            boolean sendCode = path.equals(RESTMappingV1.PANEL_AUTH + "/send-email-code")
                               || path.equals(RESTMappingV1.PANEL_AUTH + "/email/send-code");
            return sendCode ? RateLimitTier.AUTH_SEND_CODE : RateLimitTier.AUTH;
        }

        if (path.startsWith(RESTMappingV1.PANEL_MIGRATION + "/")) {
            return path.equals(RESTMappingV1.PANEL_MIGRATION + "/status") && "GET".equalsIgnoreCase(method)
                   ? RateLimitTier.MIGRATION_STATUS : RateLimitTier.MIGRATION;
        }

        if (RouteGroups.isPanelChild(path)) {
            return resolvePanelTier(path, method);
        }

        if (RouteGroups.isPublicChild(path)) {
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
        if (RouteGroups.isPublicReplayLiteChild(path) && isWriteMethod(method)) {
            return RateLimitTier.REPLAY_LITE_LABEL;
        }
        if (path.startsWith(RESTMappingV1.PUBLIC_MEDIA + "/") && isWriteMethod(method)) {
            return RateLimitTier.PUBLIC_MEDIA_UPLOAD;
        }
        if (path.startsWith(RESTMappingV1.PUBLIC_APPEALS) && isWriteMethod(method)
            && (path.contains("/verify") || path.contains("/request-verification"))) {
            return RateLimitTier.PUBLIC_TICKET_VERIFY;
        }
        if (path.startsWith(RESTMappingV1.PUBLIC_TICKETS) && isWriteMethod(method)) {
            if (path.contains("/verify") || path.contains("/request-verification")) {
                return RateLimitTier.PUBLIC_TICKET_VERIFY;
            }
            if (path.equals(RESTMappingV1.PUBLIC_TICKETS) || path.equals(RESTMappingV1.PUBLIC_TICKETS + "/unfinished")) {
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
