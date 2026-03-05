package gg.modl.backend.ticket.data;

public enum TicketType {
    BUG("bug", "Bug Report"),
    PLAYER("player", "Player Report"),
    CHAT("chat", "Chat Report"),
    APPEAL("appeal", "Ban Appeal"),
    STAFF("staff", "Staff Application"),
    SUPPORT("support", "General Support");

    private final String id;
    private final String displayName;

    TicketType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TicketType fromId(String id) {
        for (TicketType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return SUPPORT;
    }

    public static String getPrefix(TicketType type) {
        return switch (type) {
            case BUG -> "BUG";
            case PLAYER -> "PLAYER";
            case CHAT -> "CHAT";
            case APPEAL -> "APPEAL";
            case STAFF -> "STAFF";
            case SUPPORT -> "SUPPORT";
        };
    }
}
