package gg.modl.backend.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MongoKeyUtils {
    private MongoKeyUtils() {}

    /**
     * Sanitize map keys for MongoDB storage by replacing dots with the Unicode
     * full-width full stop (U+FF0E). MongoDB does not allow dots in map keys
     * because dots are used as path separators in field names.
     * Handles nested maps recursively.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitizeKeys(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().replace('.', '\uFF0E');
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = sanitizeKeys((Map<String, Object>) value);
            }
            sanitized.put(key, value);
        }
        return sanitized;
    }

    /**
     * Resolve a field key to a human-readable label using the provided mapping.
     * Falls back to formatting the key directly if no label mapping exists.
     */
    public static String resolveFieldLabel(String key, Map<String, String> fieldLabels) {
        if (fieldLabels != null && fieldLabels.containsKey(key)) {
            return fieldLabels.get(key);
        }
        return formatFieldKey(key);
    }

    /**
     * Format a raw field key (e.g. "myField_name") into a human-readable label
     * (e.g. "My Field Name").
     */
    public static String formatFieldKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }

        String formatted = key.replace("_", " ");

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isWhitespace(formatted.charAt(i - 1))) {
                result.append(' ');
            }
            result.append(c);
        }
        formatted = result.toString();

        String[] words = formatted.split("\\s+");
        StringBuilder titleCase = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                titleCase.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    titleCase.append(word.substring(1).toLowerCase());
                }
                if (i < words.length - 1) {
                    titleCase.append(" ");
                }
            }
        }
        return titleCase.toString();
    }
}
