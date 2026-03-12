package gg.modl.backend.database;

public final class CollectionName {
    // Global
    public static final String MODL_SERVERS = "servers";
    public static final String METRIC_SNAPSHOTS = "metric_snapshots";

    // Core
    public static final String PLAYERS = "players";
    public static final String SESSIONS = "sessions";
    public static final String AUTH_CODES = "auth_codes";
    public static final String SETTINGS = "settings";

    // Staff & Roles
    public static final String STAFF = "staffs";
    public static final String STAFF_ROLES = "staffroles";
    public static final String INVITATIONS = "invitations";

    // Tickets
    public static final String TICKETS = "tickets";
    public static final String TICKET_VERIFICATIONS = "ticket_verifications";
    public static final String CHAT_LOGS = "chat_logs";
    public static final String COMMAND_LOGS = "command_logs";

    // Audit
    public static final String LOGS = "logs";

    // WebAuthn
    public static final String WEBAUTHN_CREDENTIALS = "webauthn_credentials";
    public static final String WEBAUTHN_CHALLENGES = "webauthn_challenges";

    // Knowledgebase & Homepage
    public static final String KNOWLEDGEBASE_CATEGORIES = "knowledgebasecategories";
    public static final String KNOWLEDGEBASE_ARTICLES = "knowledgebasearticles";
    public static final String HOMEPAGE_CARDS = "homepagecards";
}
