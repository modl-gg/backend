package gg.modl.backend.infrastructure.config;

import gg.modl.backend.auth.AuthConfiguration;
import gg.modl.backend.ticket.config.TicketEmailVerificationConfiguration;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCodeHashSecretGuard {
    private final Environment environment;
    private final AuthConfiguration authConfiguration;
    private final TicketEmailVerificationConfiguration ticketEmailVerificationConfiguration;
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    @PostConstruct
    public void validate() {
        boolean isProductionProfile = ProfileEnvironment.hasAnyActiveProfile(environment, PRODUCTION_PROFILES);
        boolean authSecretMissing = !StringUtils.hasText(authConfiguration.getCodeHashSecret());
        boolean ticketSecretMissing = !StringUtils.hasText(ticketEmailVerificationConfiguration.getCodeHashSecret());

        if (isProductionProfile && (authSecretMissing || ticketSecretMissing)) {
            throw new IllegalStateException(
                "FATAL: modl.auth.code-hash-secret (and/or modl.ticket.email-verification.code-hash-secret) "
                + "is not set with a production Spring profile active. Login and ticket-verification codes "
                + "would be stored as unsalted, keyless SHA-256 over a 6-digit code (10^6 keyspace) -- "
                + "trivially reversible from a database leak. Set MODL_AUTH_CODE_HASH_SECRET to a strong "
                + "random secret before deploying."
            );
        }

        if (authSecretMissing || ticketSecretMissing) {
            log.warn("======================================================================");
            log.warn("  AUTH CODE HASH SECRET IS NOT SET");
            log.warn("  Email/admin login and ticket verification codes are hashed with");
            log.warn("  unsalted SHA-256 (reversible). Do NOT use this setting in production.");
            log.warn("======================================================================");
        }
    }
}
