package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum TicketCategory {
    BUG("bug", "Bug Report", "BUG"),
    PLAYER("player", "Player Report", "PLAYER"),
    CHAT("chat", "Chat Report", "CHAT"),
    APPEAL("appeal", "Ban Appeal", "APPEAL"),
    APPLICATION("application", "Staff Application", "STAFF"),
    SUPPORT("support", "General Support", "SUPPORT");

    private final String id;
    private final String displayName;
    private final String ticketPrefix;
    private static final Map<String, TicketCategory> BY_CANONICAL_ID = new LinkedHashMap<>();

    static {
        for (TicketCategory category : values()) {
            BY_CANONICAL_ID.put(category.id, category);
        }
        registerAlias(BUG, "bug_report");
        registerAlias(PLAYER, "player_report");
        registerAlias(CHAT, "chat_report");
        registerAlias(APPEAL, "ban_appeal");
        registerAlias(APPLICATION, "staff");
        registerAlias(APPLICATION, "staff_application");
        registerAlias(APPLICATION, "apply");
        registerAlias(SUPPORT, "general_support");
        registerAlias(PLAYER, "report");
    }

    TicketCategory(String id, String displayName, String ticketPrefix) {
        this.id = id;
        this.displayName = displayName;
        this.ticketPrefix = ticketPrefix;
    }

    public static List<String> reportCategoryIds() {
        return List.of(PLAYER.id, CHAT.id);
    }

    public static List<String> categoryIdsForBucket(String bucket) {
        return switch (normalize(bucket)) {
            case "bug" -> List.of(BUG.id);
            case "report" -> List.of(PLAYER.id, CHAT.id);
            case "appeal" -> List.of(APPEAL.id);
            case "staff" -> List.of(APPLICATION.id);
            case "support" -> List.of(SUPPORT.id);
            default -> List.of();
        };
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

    public static boolean isCanonicalBucket(String value) {
        return value != null && switch (value) {
            case "bug", "report", "appeal", "support", "staff" -> true;
            default -> false;
        };
    }

    @JsonCreator
    public static TicketCategory fromValue(String value) {
        return fromCanonicalId(value);
    }

    public static TicketCategory fromCanonicalId(String value) {
        TicketCategory category = BY_CANONICAL_ID.get(normalize(value));
        if (category == null) {
            throw new IllegalArgumentException("Unknown ticket category: " + value);
        }
        return category;
    }

    private static void registerAlias(TicketCategory category, String alias) {
        BY_CANONICAL_ID.put(normalize(alias), category);
    }

    @JsonValue
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTicketPrefix() {
        return ticketPrefix;
    }

    public boolean isReport() {
        return this == PLAYER || this == CHAT;
    }

    public boolean isAppeal() {
        return this == APPEAL;
    }
}
