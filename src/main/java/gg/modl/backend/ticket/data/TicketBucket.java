package gg.modl.backend.ticket.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum TicketBucket {
    BUG("bug", "Bug"),
    REPORT("report", "Report"),
    APPEAL("appeal", "Appeal"),
    SUPPORT("support", "Support"),
    STAFF("staff", "Application");

    private final String id;
    private final String displayName;
    private static final Map<String, TicketBucket> BY_CANONICAL_ID = new LinkedHashMap<>();

    static {
        for (TicketBucket bucket : values()) {
            BY_CANONICAL_ID.put(bucket.id, bucket);
        }
        registerAlias(REPORT, "player");
        registerAlias(REPORT, "player_report");
        registerAlias(REPORT, "chat");
        registerAlias(REPORT, "chat_report");
        registerAlias(BUG, "bug_report");
        registerAlias(STAFF, "application");
        registerAlias(STAFF, "staff_application");
        registerAlias(STAFF, "apply");
    }

    TicketBucket(String id, String displayName) {
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

    @JsonCreator
    public static TicketBucket fromValue(String value) {
        return fromCanonicalId(value);
    }

    public static TicketBucket fromCanonicalId(String value) {
        TicketBucket bucket = BY_CANONICAL_ID.get(normalize(value));
        if (bucket == null) {
            throw new IllegalArgumentException("Unknown ticket bucket: " + value);
        }
        return bucket;
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

    private static void registerAlias(TicketBucket bucket, String alias) {
        BY_CANONICAL_ID.put(normalize(alias), bucket);
    }
}
