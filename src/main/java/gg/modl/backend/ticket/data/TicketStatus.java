package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import gg.modl.backend.infrastructure.util.CanonicalAliasIndex;

public enum TicketStatus {
    UNFINISHED("unfinished", "Unfinished"),
    OPEN("open", "Open"),
    CLOSED("closed", "Closed");

    private final String id;
    private final String displayName;
    private static final CanonicalAliasIndex<TicketStatus> INDEX = CanonicalAliasIndex
        .of("ticket status", values(), TicketStatus::getId)
        .alias(UNFINISHED, "draft")
        .alias(OPEN, "new")
        .alias(OPEN, "active")
        .alias(OPEN, "pending")
        .alias(OPEN, "in_progress")
        .alias(OPEN, "inprogress")
        .alias(CLOSED, "resolved")
        .alias(CLOSED, "complete")
        .alias(CLOSED, "completed")
        .alias(CLOSED, "done");

    TicketStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @JsonCreator
    public static TicketStatus fromValue(String value) {
        return fromCanonicalId(value);
    }

    public static TicketStatus fromCanonicalId(String value) {
        return INDEX.resolve(value);
    }

    @JsonValue
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
