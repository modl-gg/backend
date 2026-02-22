package gg.modl.backend.ticket.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TicketAssigneeUtil {
    public static final int MAX_ASSIGNEES = 20;

    private TicketAssigneeUtil() {
    }

    public static String normalizeSingle(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    public static List<String> normalizeCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        String[] split = value.split(",");
        List<String> items = new ArrayList<>(split.length);
        for (String item : split) {
            items.add(item);
        }
        return normalizeCollection(items);
    }

    public static List<String> normalizeCollection(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String token = normalizeSingle(value);
            if (token == null) {
                continue;
            }
            normalized.add(token);
            if (normalized.size() >= MAX_ASSIGNEES) {
                break;
            }
        }

        if (normalized.isEmpty()) {
            return List.of();
        }

        return List.copyOf(normalized);
    }

    public static String toDisplayString(List<String> assignedTo) {
        if (assignedTo == null || assignedTo.isEmpty()) {
            return "";
        }
        return String.join(", ", assignedTo);
    }
}
