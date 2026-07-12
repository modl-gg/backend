package gg.modl.backend.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DevelopmentModeGuard {
    private final Environment environment;
    private final ModlProperties modlProperties;
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test");

    @PostConstruct
    public void validate() {
        if (!modlProperties.isDevelopmentMode()) {
            return;
        }

        boolean isDevProfile = ProfileEnvironment.hasAnyActiveProfile(environment, DEV_PROFILES);
        boolean explicitlyAllowed = environment.getProperty("MODL_ALLOW_DEV_MODE", Boolean.class, false);

        if (!isDevProfile && !explicitlyAllowed) {
            throw new IllegalStateException(
                "FATAL: modl.development-mode=true is set but no development context was detected. " +
                "Development mode disables CSRF protection, captcha validation, and weakens cookie security, " +
                "and must never run in production. To run development mode, activate a dev/local/test Spring " +
                "profile (e.g. SPRING_PROFILES_ACTIVE=dev) or set MODL_ALLOW_DEV_MODE=true. For production, " +
                "remove modl.development-mode or set it to false."
            );
        }

        log.warn("======================================================================");
        log.warn("  DEVELOPMENT MODE IS ENABLED");
        log.warn("  CSRF protection, captcha validation, and cookie security are relaxed.");
        log.warn("  Do NOT use this setting in production.");
        log.warn("======================================================================");
    }
}
