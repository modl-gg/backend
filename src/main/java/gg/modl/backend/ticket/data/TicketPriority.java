package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum TicketPriority {
    LOW("low", "Low"),
    NORMAL("normal", "Normal"),
    HIGH("high", "High");

    private final String id;
    private final String displayName;
    private static final Map<String, TicketPriority> BY_CANONICAL_ID = new LinkedHashMap<>();

    static {
        for (TicketPriority priority : values()) {
            BY_CANONICAL_ID.put(priority.id, priority);
        }
        registerAlias(LOW, "minor");
        registerAlias(NORMAL, "medium");
        registerAlias(NORMAL, "default");
        registerAlias(NORMAL, "standard");
        registerAlias(HIGH, "urgent");
        registerAlias(HIGH, "critical");
        registerAlias(HIGH, "highest");
    }

    TicketPriority(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static TicketPriority resolveOrDefault(String priority) {
        return priority == null || priority.isBlank() ? NORMAL : fromCanonicalId(priority);
    }

    public static TicketPriority fromCanonicalId(String value) {
        TicketPriority priority = BY_CANONICAL_ID.get(normalize(value));
        if (priority == null) {
            throw new IllegalArgumentException("Unknown ticket priority: " + value);
        }
        return priority;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    @JsonCreator
    public static TicketPriority fromValue(String value) {
        return fromCanonicalId(value);
    }

    private static void registerAlias(TicketPriority priority, String alias) {
        BY_CANONICAL_ID.put(normalize(alias), priority);
    }

    @JsonValue
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
