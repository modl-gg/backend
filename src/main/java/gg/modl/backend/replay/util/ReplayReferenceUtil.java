package gg.modl.backend.replay.util;

import gg.modl.backend.infrastructure.exception.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ReplayReferenceUtil {
    private static final String REPLAY_ID_QUERY_PARAM = "id";

    private ReplayReferenceUtil() {
    }

    public static String requireValidUuid(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Target UUID must be a valid UUID");
        }
    }

    public static String extractReplayId(String replayReference) {
        String normalized = normalize(replayReference);
        if (normalized == null) {
            return null;
        }
        if (isRawReplayId(normalized)) {
            return normalized;
        }
        try {
            URI uri = new URI(normalized);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                String key = separator >= 0 ? pair.substring(0, separator) : pair;
                if (!REPLAY_ID_QUERY_PARAM.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                    continue;
                }
                String value = separator >= 0 ? pair.substring(separator + 1) : "";
                return normalize(URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return null;
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return null;
        }
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isRawReplayId(String replayReference) {
        return !replayReference.contains("://")
               && !replayReference.contains("/")
               && !replayReference.contains("?")
               && !replayReference.contains("#");
    }
}
