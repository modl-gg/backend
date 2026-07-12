package gg.modl.backend.migration.validation;

import gg.modl.backend.infrastructure.validation.RegExpConstants;
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

        return UUID_PATTERN.matcher(uuid).matches() || UUID_NO_DASHES_PATTERN.matcher(uuid).matches();
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

        if (dateObj instanceof Date date) {
            return date;
        }

        if (dateObj instanceof Number number) {
            return epochToDate(number.longValue());
        }

        if (dateObj instanceof String dateStr) {
            try {
                return Date.from(Instant.parse(dateStr));
            } catch (DateTimeParseException e) {
                try {
                    return epochToDate(Long.parseLong(dateStr));
                } catch (NumberFormatException ex) {
                    log.warn("Unable to parse date: {}", dateStr);
                    return null;
                }
            }
        }

        return null;
    }

    private Date epochToDate(long timestamp) {
        if (timestamp > 100_000_000_000L) {
            return new Date(timestamp);
        }
        return new Date(timestamp * 1000);
    }

    public boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        return ip.indexOf(':') >= 0 ? isValidIpv6(ip) : isValidIpv4(ip);
    }

    private static boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!isValidIpv4Octet(octet)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIpv4Octet(String octet) {
        int length = octet.length();
        if (length < 1 || length > 3) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = octet.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        if (length > 1 && octet.charAt(0) == '0') {
            return false;
        }
        return Integer.parseInt(octet) <= 255;
    }

    private static boolean isValidIpv6(String value) {
        int compressionIndex = value.indexOf("::");
        boolean compressed = compressionIndex >= 0;
        if (compressed && value.indexOf("::", compressionIndex + 1) >= 0) {
            return false;
        }

        String[] sides = compressed
                         ? new String[]{value.substring(0, compressionIndex), value.substring(compressionIndex + 2)}
                         : new String[]{value};

        int groupCount = 0;
        for (int side = 0; side < sides.length; side++) {
            if (sides[side].isEmpty()) {
                continue;
            }
            String[] groups = sides[side].split(":", -1);
            for (int i = 0; i < groups.length; i++) {
                boolean lastGroup = side == sides.length - 1 && i == groups.length - 1;
                String group = groups[i];
                if (group.indexOf('.') >= 0) {
                    if (!lastGroup || !isValidIpv4(group)) {
                        return false;
                    }
                    groupCount += 2;
                } else {
                    if (!isValidHextet(group)) {
                        return false;
                    }
                    groupCount += 1;
                }
            }
        }

        return compressed ? groupCount < 8 : groupCount == 8;
    }

    private static boolean isValidHextet(String group) {
        int length = group.length();
        if (length < 1 || length > 4) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = group.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
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
