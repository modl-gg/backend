package gg.modl.backend.registration;

import gg.modl.backend.beta.SubdomainValidator;
import gg.modl.backend.infrastructure.config.ModlProperties;
import gg.modl.backend.email.EmailHTMLTemplate;
import gg.modl.backend.email.EmailService;
import gg.modl.backend.infrastructure.ratelimit.BucketPool;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.infrastructure.turnstile.TurnstileService;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.settings.service.ApiKeySettingsService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final EmailService emailService;
    private final ApiKeySettingsService apiKeySettingsService;
    private final ModlProperties modlProperties;
    private final ServerService serverService;
    private final TurnstileService turnstileService;
    private final SubdomainValidator subdomainValidator;
    private final BucketPool bucketPool;

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int REGISTRATION_CAPACITY = 1;
    private static final long NANOS_PER_MINUTE = 60_000_000_000L;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final Duration CLI_RATE_LIMIT_WINDOW = Duration.ofMinutes(30);
    private static final long AUTO_LOGIN_TOKEN_EXPIRY_MS = 10 * 60 * 1000;
    private static final ServerPlan PUBLIC_REGISTRATION_PLAN = ServerPlan.FREE;

    private record RateLimitResult(boolean limited, long remainingMinutes) {}

    public enum RegistrationChannel {
        WEB(RATE_LIMIT_WINDOW, "You can only register one server every 10 minutes."),
        CLI(CLI_RATE_LIMIT_WINDOW, "CLI registration is limited to once every 30 minutes.");

        private final Duration window;
        private final String cooldownMessage;

        RegistrationChannel(Duration window, String cooldownMessage) {
            this.window = window;
            this.cooldownMessage = cooldownMessage;
        }

        Duration window() {
            return window;
        }

        String cooldownMessage() {
            return cooldownMessage;
        }
    }

    public record RegistrationCommand(RegistrationChannel channel, boolean requireTurnstile, String turnstileToken,
                                      String clientIp, String email, String serverName, String customDomain) {}

    public record RegistrationRejection(HttpStatus status, String message) {}

    public sealed interface RegistrationOutcome {
        record Created(Server server) implements RegistrationOutcome {}

        record Rejected(RegistrationRejection rejection) implements RegistrationOutcome {}
    }

    public RegistrationOutcome performRegistration(RegistrationCommand command) {
        String subdomain = subdomainValidator.normalize(command.customDomain());
        ServerService.ServerExistResult exist = lookupIdentity(command.email(), command.serverName(), subdomain);

        Optional<RegistrationRejection> publicRejection = rejectPublicIdentity(subdomain, exist);
        if (publicRejection.isPresent()) {
            return new RegistrationOutcome.Rejected(publicRejection.get());
        }

        RateLimitResult rateLimit = reserve(command.channel(), command.clientIp());
        if (rateLimit.limited()) {
            return new RegistrationOutcome.Rejected(new RegistrationRejection(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. " + command.channel().cooldownMessage()
                    + " Please try again in " + rateLimit.remainingMinutes() + " minute(s)."));
        }
        if (command.requireTurnstile()
            && !turnstileService.validateToken(command.turnstileToken(), command.clientIp())) {
            log.warn("Turnstile validation failed for {} registration attempt", command.channel());
            return new RegistrationOutcome.Rejected(new RegistrationRejection(HttpStatus.BAD_REQUEST,
                "Security verification failed. Please try again."));
        }

        Optional<RegistrationRejection> emailRejection = rejectDuplicateEmail(exist);
        if (emailRejection.isPresent()) {
            return new RegistrationOutcome.Rejected(emailRejection.get());
        }

        String emailVerificationToken = generateToken();
        Server server;
        try {
            server = serverService.createServer(command.serverName(), subdomain, command.email(),
                emailVerificationToken, PUBLIC_REGISTRATION_PLAN);
        } catch (Exception e) {
            log.error("Failed to create server via {} registration", command.channel(), e);
            return new RegistrationOutcome.Rejected(new RegistrationRejection(HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal server error occurred during registration. Please try again later."));
        }
        sendVerificationEmail(command.email(), subdomain, emailVerificationToken);
        return new RegistrationOutcome.Created(server);
    }

    public Optional<RegistrationRejection> validateIdentity(String email, String serverName, String normalizedSubdomain) {
        ServerService.ServerExistResult exist = lookupIdentity(email, serverName, normalizedSubdomain);
        Optional<RegistrationRejection> publicRejection = rejectPublicIdentity(normalizedSubdomain, exist);
        if (publicRejection.isPresent()) {
            return publicRejection;
        }
        return rejectDuplicateEmail(exist);
    }

    private ServerService.ServerExistResult lookupIdentity(String email, String serverName, String normalizedSubdomain) {
        return serverService.doesServerExist(
            email != null ? email : "",
            serverName != null ? serverName : "",
            normalizedSubdomain != null ? normalizedSubdomain : "");
    }

    private Optional<RegistrationRejection> rejectPublicIdentity(String normalizedSubdomain,
                                                                 ServerService.ServerExistResult exist) {
        if (!subdomainValidator.matchesFormat(normalizedSubdomain)) {
            return Optional.of(new RegistrationRejection(HttpStatus.BAD_REQUEST, subdomainValidator.formatMessage()));
        }
        if (subdomainValidator.isReserved(normalizedSubdomain)) {
            return Optional.of(new RegistrationRejection(HttpStatus.CONFLICT, subdomainValidator.reservedMessage()));
        }
        if (exist.nameMatch()) {
            return Optional.of(new RegistrationRejection(HttpStatus.CONFLICT, "This server name is already taken."));
        }
        if (exist.domainMatch()) {
            return Optional.of(new RegistrationRejection(HttpStatus.CONFLICT, "This subdomain is already in use."));
        }
        return Optional.empty();
    }

    private Optional<RegistrationRejection> rejectDuplicateEmail(ServerService.ServerExistResult exist) {
        if (exist.emailMatch()) {
            return Optional.of(new RegistrationRejection(HttpStatus.CONFLICT,
                "An account with this email already exists."));
        }
        return Optional.empty();
    }

    private RateLimitResult reserve(RegistrationChannel channel, String clientIp) {
        Bucket bucket = bucketPool.resolveBucket(
            "registration:" + channel.name(), clientIp, REGISTRATION_CAPACITY, channel.window());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new RateLimitResult(false, 0);
        }
        long remainingMinutes = Math.max(1, (probe.getNanosToWaitForRefill() + NANOS_PER_MINUTE - 1) / NANOS_PER_MINUTE);
        return new RateLimitResult(true, remainingMinutes);
    }

    public String generateToken() {
        return RequestUtil.generateSecureToken(TOKEN_BYTE_LENGTH);
    }

    public Date createAutoLoginTokenExpiry() {
        return new Date(System.currentTimeMillis() + AUTO_LOGIN_TOKEN_EXPIRY_MS);
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
