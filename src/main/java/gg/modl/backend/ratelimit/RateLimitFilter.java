package gg.modl.backend.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitConfig rateLimitConfig;

    private static final String RATE_LIMIT_REMAINING_HEADER = "X-Rate-Limit-Remaining";
    private static final String RATE_LIMIT_RETRY_AFTER_HEADER = "X-Rate-Limit-Retry-After-Seconds";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        RateLimitConfig.RateLimitTier tier = rateLimitConfig.getTierForPath(path, method);
        Bucket bucket = rateLimitConfig.resolveBucket(clientKey + ":" + tier.name(), tier);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader(RATE_LIMIT_RETRY_AFTER_HEADER, String.valueOf(waitTimeSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":" + waitTimeSeconds + "}");

            log.warn("Rate limit exceeded for client {} on path {} (tier: {})", clientKey, path, tier.name());
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String serverDomain = request.getHeader("X-Server-Domain");
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String realIp = request.getHeader("X-Real-IP");

        String ip;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            ip = forwardedFor.split(",")[0].trim();
        } else if (realIp != null && !realIp.isBlank()) {
            ip = realIp;
        } else {
            ip = request.getRemoteAddr();
        }

        if (serverDomain != null && !serverDomain.isBlank()) {
            return serverDomain + ":" + ip;
        }

        return ip;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
