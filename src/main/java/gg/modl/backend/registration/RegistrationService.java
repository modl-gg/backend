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

    public RateLimitResult checkRateLimit(String clientIp) {
        return checkLimit(rateLimitMap, RATE_LIMIT_WINDOW_MS, clientIp);
    }

    public RateLimitResult checkCliRateLimit(String clientIp) {
        return checkLimit(cliRateLimitMap, CLI_RATE_LIMIT_WINDOW_MS, clientIp);
    }

    public void recordRateLimit(String clientIp) {
        rateLimitMap.put(clientIp, System.currentTimeMillis());
    }

    public void recordCliRateLimit(String clientIp) {
        cliRateLimitMap.put(clientIp, System.currentTimeMillis());
    }

    private RateLimitResult checkLimit(ConcurrentHashMap<String, Long> map, long windowMs, String clientIp) {
        long now = System.currentTimeMillis();
        Long last = map.get(clientIp);
        if (last != null && (now - last) < windowMs) {
            return new RateLimitResult(true, (windowMs - (now - last)) / 1000 / 60 + 1);
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
        if (plan != null && plan.equalsIgnoreCase("premium")) {
            return ServerPlan.PREMIUM;
        }
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
