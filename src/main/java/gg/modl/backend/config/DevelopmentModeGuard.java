package gg.modl.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@Slf4j
public class DevelopmentModeGuard {
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    @Value("${modl.development-mode:false}")
    private boolean developmentMode;

    private final Environment environment;

    public DevelopmentModeGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!developmentMode) {
            return;
        }

        boolean isProductionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> PRODUCTION_PROFILES.contains(profile.toLowerCase()));

        if (isProductionProfile) {
            throw new IllegalStateException(
                    "FATAL: modl.development-mode=true is set with a production Spring profile active. " +
                    "Development mode disables CSRF protection, captcha validation, and weakens cookie security. " +
                    "Remove modl.development-mode or set it to false for production deployments."
            );
        }

        log.warn("======================================================================");
        log.warn("  DEVELOPMENT MODE IS ENABLED");
        log.warn("  CSRF protection, captcha validation, and cookie security are relaxed.");
        log.warn("  Do NOT use this setting in production.");
        log.warn("======================================================================");
    }
}
