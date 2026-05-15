package gg.modl.backend.infrastructure.ratelimit;

import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitConfig rateLimitConfig;
    private final ProtobufErrorResponseWriter protobufErrorResponseWriter;

    // Canonical response headers
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RETRY_AFTER_HEADER = "X-RateLimit-Retry-After";
    // Legacy aliases kept for compatibility with existing clients/proxies
    private static final String RATE_LIMIT_REMAINING_HEADER_LEGACY = "X-Rate-Limit-Remaining";
    private static final String RATE_LIMIT_RETRY_AFTER_HEADER_LEGACY = "X-Rate-Limit-Retry-After-Seconds";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

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
            String remaining = String.valueOf(probe.getRemainingTokens());
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, remaining);
            response.setHeader(RATE_LIMIT_REMAINING_HEADER_LEGACY, remaining);
            filterChain.doFilter(request, response);
        } else {
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            String retryAfter = String.valueOf(waitTimeSeconds);
            response.setHeader(RATE_LIMIT_RETRY_AFTER_HEADER, retryAfter);
            response.setHeader(RATE_LIMIT_RETRY_AFTER_HEADER_LEGACY, retryAfter);
            if (protobufErrorResponseWriter.shouldWriteProtobuf(request)) {
                protobufErrorResponseWriter.write(
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RESOURCE_EXHAUSTED",
                    "Rate limit exceeded"
                );
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":" + waitTimeSeconds + "}");
            }

            log.warn("Rate limit exceeded for client {} on path {} (tier: {})", clientKey, path, tier.name());
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        return RequestUtil.getClientIp(request);
    }
}
