package gg.modl.backend.migration.validation;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
import java.net.InetAddress;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MigrationValidator {
    public static final int MAX_PLAYER_RECORDS = 1_000_000;

    private static final Pattern UUID_PATTERN = Pattern.compile(RegExpConstants.UUID);
    private static final Pattern UUID_NO_DASHES_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

    public ValidationResult validateHeader(boolean playersPresent, boolean playersIsArray,
                                           Integer declaredPlayerCount) {
        if (!playersPresent) {
            return ValidationResult.error("Missing 'players' field");
        }
        if (!playersIsArray) {
            return ValidationResult.error("'players' field must be an array");
        }
        if (declaredPlayerCount != null) {
            if (declaredPlayerCount <= 0) {
                return ValidationResult.error("Players array cannot be empty");
            }
            if (declaredPlayerCount > MAX_PLAYER_RECORDS) {
                return ValidationResult.error("Players array exceeds maximum length of 1,000,000");
            }
        }
        return ValidationResult.success(declaredPlayerCount == null ? 0 : declaredPlayerCount);
    }

    public boolean isValidUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }

        if (UUID_PATTERN.matcher(uuid).matches()) {
            return true;
        }

        if (UUID_NO_DASHES_PATTERN.matcher(uuid).matches()) {
            return true;
        }

        return false;
    }

    public String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }

        String cleaned = uuid.replace("-", "").toLowerCase();

        if (cleaned.length() != 32) {
            return uuid;
        }

        return cleaned.substring(0, 8) + "-" +
               cleaned.substring(8, 12) + "-" +
               cleaned.substring(12, 16) + "-" +
               cleaned.substring(16, 20) + "-" +
               cleaned.substring(20);
    }

    public String sanitizeString(String input, int maxLength) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }

        return trimmed
            .replace("\u0000", "")
            .replace("\r", "");
    }

    public Date parseDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }

        if (dateObj instanceof Date) {
            return (Date) dateObj;
        }

        if (dateObj instanceof Number) {
            long timestamp = ((Number) dateObj).longValue();
            if (timestamp > 100_000_000_000L) {
                return new Date(timestamp);
            } else {
                return new Date(timestamp * 1000);
            }
        }

        if (dateObj instanceof String) {
            String dateStr = (String) dateObj;
            try {
                return Date.from(Instant.parse(dateStr));
            } catch (DateTimeParseException e) {
                try {
                    long timestamp = Long.parseLong(dateStr);
                    if (timestamp > 100_000_000_000L) {
                        return new Date(timestamp);
                    } else {
                        return new Date(timestamp * 1000);
                    }
                } catch (NumberFormatException ex) {
                    log.warn("Unable to parse date: {}", dateStr);
                    return null;
                }
            }
        }

        return null;
    }

    public boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record ValidationResult(boolean valid, String error, int playerCount) {
        public static ValidationResult success(int playerCount) {
            return new ValidationResult(true, null, playerCount);
        }

        public static ValidationResult error(String error) {
            return new ValidationResult(false, error, 0);
        }
    }
}
