package gg.modl.backend.beta;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SubdomainValidator {
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
        "payments", "payment", "api", "app",
        "status", "mail", "www", "discord",
        "admin", "twitter", "demo", "panel",
        "ftp", "sftp", "www2", "www3",
        "billing", "stripe", "test", "staging",
        "root", "internal", "administrator", "mod",
        "beta", "dev", "portal", "dashboard",
        "modl", "support", "help", "email",
        "docs", "secure", "alpha", "cdn",
        "nexus", "replay", "replays"
    );
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 50;
    private static final Pattern FORMAT_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final String FORMAT_MESSAGE =
        "Subdomain must be 3-50 characters, lowercase letters, digits and hyphens only, and cannot start or end with a hyphen.";
    private static final String RESERVED_MESSAGE = "This subdomain is reserved and cannot be used.";

    public String normalize(String subdomain) {
        return subdomain == null ? null : subdomain.trim().toLowerCase(Locale.ROOT);
    }

    public boolean matchesFormat(String subdomain) {
        return subdomain != null
            && subdomain.length() >= MIN_LENGTH
            && subdomain.length() <= MAX_LENGTH
            && FORMAT_PATTERN.matcher(subdomain).matches();
    }

    public boolean isReserved(String subdomain) {
        String normalized = normalize(subdomain);
        return normalized != null && RESERVED_SUBDOMAINS.contains(normalized);
    }

    public Optional<String> validate(String subdomain) {
        String normalized = normalize(subdomain);
        if (!matchesFormat(normalized)) {
            return Optional.of(FORMAT_MESSAGE);
        }
        if (isReserved(normalized)) {
            return Optional.of(RESERVED_MESSAGE);
        }
        return Optional.empty();
    }

    public String formatMessage() {
        return FORMAT_MESSAGE;
    }

    public String reservedMessage() {
        return RESERVED_MESSAGE;
    }
}
