package gg.modl.backend.infrastructure.validation;

import gg.modl.backend.infrastructure.exception.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;

public final class SafeUrls {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String PROTOCOL_RELATIVE_PREFIX = "//";
    private static final String BACKSLASH_RELATIVE_PREFIX = "/\\";
    private static final String ROOT_RELATIVE_PREFIX = "/";

    public static boolean isSafe(String url) {
        if (url == null) {
            return true;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (containsControlCharacters(trimmed)) {
            return false;
        }
        if (trimmed.startsWith(PROTOCOL_RELATIVE_PREFIX) || trimmed.startsWith(BACKSLASH_RELATIVE_PREFIX)) {
            return false;
        }
        if (trimmed.startsWith(ROOT_RELATIVE_PREFIX)) {
            return true;
        }
        try {
            String scheme = new URI(trimmed).getScheme();
            return HTTP_SCHEME.equalsIgnoreCase(scheme) || HTTPS_SCHEME.equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static void requireSafe(String url, String errorMessage) {
        if (!isSafe(url)) {
            throw new ValidationException(errorMessage);
        }
    }

    private static boolean containsControlCharacters(String value) {
        return value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private SafeUrls() {}
}
