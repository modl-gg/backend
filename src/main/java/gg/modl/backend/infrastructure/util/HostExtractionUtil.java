package gg.modl.backend.infrastructure.util;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class HostExtractionUtil {

    @Nullable
    public String extractHost(@Nullable String originOrDomain) {
        if (originOrDomain == null || originOrDomain.isBlank()) {
            return null;
        }

        String value = originOrDomain.trim();
        String normalized = value.contains("://") ? value : "https://" + value;

        try {
            return URI.create(normalized).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public String normalizeServerDomain(@Nullable String serverDomain) {
        String host = extractHost(serverDomain);
        if (host == null || host.isBlank()) {
            return null;
        }
        return host;
    }

    public Set<String> parseCommaSeparated(@Nullable String values) {
        if (values == null || values.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(values.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
