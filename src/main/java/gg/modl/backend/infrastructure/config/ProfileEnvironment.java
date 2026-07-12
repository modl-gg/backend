package gg.modl.backend.infrastructure.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;

public final class ProfileEnvironment {
    private ProfileEnvironment() {
    }

    public static boolean hasAnyActiveProfile(Environment environment, Set<String> profiles) {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profiles.contains(profile.toLowerCase()));
    }
}
