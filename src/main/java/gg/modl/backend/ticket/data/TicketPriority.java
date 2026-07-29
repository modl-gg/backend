package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import gg.modl.backend.infrastructure.util.CanonicalAliasIndex;

public enum TicketPriority {
    LOW("low", "Low"),
    NORMAL("normal", "Normal"),
    HIGH("high", "High");

    private final String id;
    private final String displayName;
    private static final CanonicalAliasIndex<TicketPriority> INDEX = CanonicalAliasIndex
        .of("ticket priority", values(), TicketPriority::getId)
        .alias(LOW, "minor")
        .alias(NORMAL, "medium")
        .alias(NORMAL, "default")
        .alias(NORMAL, "standard")
        .alias(HIGH, "urgent")
        .alias(HIGH, "critical")
        .alias(HIGH, "highest");

    TicketPriority(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static TicketPriority resolveOrDefault(String priority) {
        return priority == null || priority.isBlank() ? NORMAL : fromCanonicalId(priority);
    }

    public static TicketPriority fromCanonicalId(String value) {
        return INDEX.resolve(value);
    }

    @JsonCreator
    public static TicketPriority fromValue(String value) {
        return fromCanonicalId(value);
    }

    @JsonValue
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
