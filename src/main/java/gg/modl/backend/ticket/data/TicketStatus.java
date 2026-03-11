package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum TicketStatus {
    UNFINISHED("unfinished", "Unfinished"),
    OPEN("open", "Open"),
    CLOSED("closed", "Closed");

    private final String id;
    private final String displayName;
    private static final Map<String, TicketStatus> BY_CANONICAL_ID = new LinkedHashMap<>();

    static {
        for (TicketStatus status : values()) {
            BY_CANONICAL_ID.put(status.id, status);
        }
        registerAlias(UNFINISHED, "draft");
        registerAlias(OPEN, "new");
        registerAlias(OPEN, "active");
        registerAlias(OPEN, "pending");
        registerAlias(OPEN, "in_progress");
        registerAlias(OPEN, "inprogress");
        registerAlias(CLOSED, "resolved");
        registerAlias(CLOSED, "complete");
        registerAlias(CLOSED, "completed");
        registerAlias(CLOSED, "done");
    }

    TicketStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @JsonCreator
    public static TicketStatus fromValue(String value) {
        return fromCanonicalId(value);
    }

    public static TicketStatus fromCanonicalId(String value) {
        TicketStatus status = BY_CANONICAL_ID.get(normalize(value));
        if (status == null) {
            throw new IllegalArgumentException("Unknown ticket status: " + value);
        }
        return status;
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

    private static void registerAlias(TicketStatus status, String alias) {
        BY_CANONICAL_ID.put(normalize(alias), status);
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
