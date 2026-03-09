package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum AppealWorkflowStatus {
    OPEN("open", "Open"),
    UNDER_REVIEW("under_review", "Under Review"),
    PENDING_PLAYER_RESPONSE("pending_player_response", "Pending Player Response"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected");

    private final String id;
    private final String displayName;
    private static final Map<String, AppealWorkflowStatus> BY_CANONICAL_ID = new LinkedHashMap<>();

    static {
        for (AppealWorkflowStatus workflowStatus : values()) {
            BY_CANONICAL_ID.put(workflowStatus.id, workflowStatus);
        }
        registerAlias(UNDER_REVIEW, "underreview");
        registerAlias(PENDING_PLAYER_RESPONSE, "pendingplayerresponse");
        registerAlias(APPROVED, "approve");
        registerAlias(APPROVED, "accepted");
        registerAlias(APPROVED, "accept");
        registerAlias(REJECTED, "reject");
        registerAlias(REJECTED, "dismiss");
        registerAlias(REJECTED, "dismissed");
        registerAlias(REJECTED, "denied");
        registerAlias(REJECTED, "deny");
    }

    AppealWorkflowStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @JsonValue
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    public TicketStatus toTicketStatus() {
        return isTerminal() ? TicketStatus.CLOSED : TicketStatus.OPEN;
    }

    @JsonCreator
    public static AppealWorkflowStatus fromValue(String value) {
        return fromCanonicalId(value);
    }

    public static AppealWorkflowStatus fromCanonicalId(String value) {
        AppealWorkflowStatus status = BY_CANONICAL_ID.get(normalize(value));
        if (status == null) {
            throw new IllegalArgumentException("Unknown appeal workflow status: " + value);
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

    private static void registerAlias(AppealWorkflowStatus status, String alias) {
        BY_CANONICAL_ID.put(normalize(alias), status);
    }
}
