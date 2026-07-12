package gg.modl.backend.infrastructure.util;

import java.util.Locale;
import java.util.UUID;

public final class UuidUtils {

    private static final String UNDASHED_UUID_PATTERN = "^[0-9a-fA-F]{32}$";
    private static final String DASH_INSERT_PATTERN = "(.{8})(.{4})(.{4})(.{4})(.{12})";
    private static final String DASH_INSERT_REPLACEMENT = "$1-$2-$3-$4-$5";

    private UuidUtils() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    public static String dashed(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.matches(UNDASHED_UUID_PATTERN)) {
            candidate = candidate.replaceFirst(DASH_INSERT_PATTERN, DASH_INSERT_REPLACEMENT);
        }
        try {
            return UUID.fromString(candidate).toString();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
