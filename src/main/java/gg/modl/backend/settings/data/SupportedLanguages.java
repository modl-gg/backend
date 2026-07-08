package gg.modl.backend.settings.data;

import java.util.Set;

public final class SupportedLanguages {
    public static final String DEFAULT = "en";

    private static final Set<String> CODES = Set.of(
        "en", "de", "es", "fr", "hi", "it", "ja", "nl", "pt", "ru", "zh"
    );

    private SupportedLanguages() {
    }

    public static boolean isSupported(String code) {
        return code != null && CODES.contains(code);
    }
}
