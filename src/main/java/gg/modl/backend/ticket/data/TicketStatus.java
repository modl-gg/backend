package gg.modl.backend.ticket.data;

public enum TicketStatus {
    UNFINISHED("Unfinished"),
    OPEN("Open"),
    CLOSED("Closed"),
    IN_PROGRESS("In Progress"),
    UNDER_REVIEW("Under Review"),
    AWAITING_RESPONSE("Awaiting Response");

    private final String displayName;

    TicketStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TicketStatus fromDisplayName(String displayName) {
        for (TicketStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        return OPEN;
    }
}
