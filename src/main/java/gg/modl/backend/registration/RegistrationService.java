package gg.modl.backend.registration;

import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final EmailService emailService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final ModlProperties modlProperties;

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
    private static final long CLI_RATE_LIMIT_WINDOW_MS = 30 * 60 * 1000;
    private static final long AUTO_LOGIN_TOKEN_EXPIRY_MS = 10 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> rateLimitMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cliRateLimitMap = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean limited, long remainingMinutes) {}

    public RateLimitResult reserveRateLimit(String clientIp) {
        return reserve(rateLimitMap, RATE_LIMIT_WINDOW_MS, clientIp);
    }

    public RateLimitResult reserveCliRateLimit(String clientIp) {
        return reserve(cliRateLimitMap, CLI_RATE_LIMIT_WINDOW_MS, clientIp);
    }

    public void releaseRateLimit(String clientIp) {
        rateLimitMap.remove(clientIp);
    }

    public void releaseCliRateLimit(String clientIp) {
        cliRateLimitMap.remove(clientIp);
    }

    private void evictExpired(ConcurrentHashMap<String, Long> map, long windowMs) {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> (now - entry.getValue()) >= windowMs);
    }

    private RateLimitResult reserve(ConcurrentHashMap<String, Long> map, long windowMs, String clientIp) {
        evictExpired(map, windowMs);
        long now = System.currentTimeMillis();
        long[] prior = { -1L };
        map.compute(clientIp, (k, last) -> {
            if (last != null && (now - last) < windowMs) {
                prior[0] = last;
                return last;
            }
            prior[0] = -1L;
            return now;
        });
        if (prior[0] != -1L) {
            return new RateLimitResult(true, (windowMs - (now - prior[0])) / 1000 / 60 + 1);
        }
        return new RateLimitResult(false, 0);
    }

    public String generateToken() {
        return RequestUtil.generateSecureToken(TOKEN_BYTE_LENGTH);
    }

    public Date createAutoLoginTokenExpiry() {
        return new Date(System.currentTimeMillis() + AUTO_LOGIN_TOKEN_EXPIRY_MS);
    }

    public ServerPlan parsePlan(String plan) {
        // Public registration must never grant privileged billing tiers from client input.
        return ServerPlan.FREE;
    }

    public void sendVerificationEmail(String email, String customDomain, String token) {
        try {
            String verificationLink = String.format("https://%s.%s/verify-email?token=%s",
                customDomain, modlProperties.getAppDomain(), token);
            EmailHTMLTemplate.HTMLEmail htmlEmail = EmailHTMLTemplate.REGISTRATION_VERIFY_LINK.build(verificationLink);
            emailService.send(email, htmlEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email", e);
        }
    }

    public boolean isTokenExpired(Server server) {
        return server.getProvisioningSignInTokenExpiresAt() != null &&
            server.getProvisioningSignInTokenExpiresAt().before(new Date());
    }

    public enum ServerReadiness { READY, PROVISIONING_INCOMPLETE, EMAIL_UNVERIFIED }

    public ServerReadiness checkServerReadiness(Server server) {
        if (server.getProvisioningStatus() != ProvisioningStatus.COMPLETED) {
            return ServerReadiness.PROVISIONING_INCOMPLETE;
        }
        if (!Boolean.TRUE.equals(server.getEmailVerified())) {
            return ServerReadiness.EMAIL_UNVERIFIED;
        }
        return ServerReadiness.READY;
    }

    public String resolveOrGenerateApiKey(Server server) {
        String apiKey = server.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = apiKeySettingsService.getApiKeyFromSettings(server);
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = apiKeySettingsService.generateApiKey(server, "default");
            apiKeySettingsService.syncApiKeyToServer(server, apiKey);
        }
        return apiKey;
    }

    public String buildPanelUrl(Server server) {
        return String.format("https://%s.%s", server.getCustomDomain(), modlProperties.getAppDomain());
    }

    public String getProvisioningMessage(ProvisioningStatus status) {
        if (status == null) {
            return "Your server is queued for setup...";
        }
        return switch (status) {
            case PENDING -> "Your server is queued for setup...";
            case IN_PROGRESS -> "Setting up your server...";
            case COMPLETED -> "Setup complete!";
            case FAILED -> "Setup failed. Please contact support.";
        };
    }
}
