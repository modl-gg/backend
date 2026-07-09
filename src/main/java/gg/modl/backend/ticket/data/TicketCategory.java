package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import gg.modl.backend.infrastructure.util.CanonicalAliasIndex;
import java.util.List;

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
    private static final CanonicalAliasIndex<TicketCategory> INDEX = CanonicalAliasIndex
        .of("ticket category", values(), TicketCategory::getId)
        .alias(BUG, "bug_report")
        .alias(PLAYER, "player_report")
        .alias(CHAT, "chat_report")
        .alias(APPEAL, "ban_appeal")
        .alias(APPLICATION, "staff")
        .alias(APPLICATION, "staff_application")
        .alias(APPLICATION, "apply")
        .alias(SUPPORT, "general_support")
        .alias(PLAYER, "report");

    TicketCategory(String id, String displayName, String ticketPrefix) {
        this.id = id;
        this.displayName = displayName;
        this.ticketPrefix = ticketPrefix;
    }

    public static List<String> reportCategoryIds() {
        return List.of(PLAYER.id, CHAT.id);
    }

    public static List<String> categoryIdsForBucket(String bucket) {
        return switch (CanonicalAliasIndex.normalize(bucket)) {
            case "bug" -> List.of(BUG.id);
            case "report" -> List.of(PLAYER.id, CHAT.id);
            case "appeal" -> List.of(APPEAL.id);
            case "staff" -> List.of(APPLICATION.id);
            case "support" -> List.of(SUPPORT.id);
            default -> List.of();
        };
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
        return INDEX.resolve(value);
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
