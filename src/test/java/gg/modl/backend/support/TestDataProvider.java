package gg.modl.backend.support;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bson.Document;

public final class TestDataProvider {

    // Defaults (used when MongoDB is not available)
    private static final String DEFAULT_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String DEFAULT_USERNAME = "Notch";
    private static final int DEFAULT_TYPE_ORDINAL = 14;

    private static volatile boolean initialized = false;
    private static List<PlayerInfo> players;
    private static PlayerInfo playerWithPunishments;
    private static PlayerInfo playerWithoutPunishments;
    private static List<PunishmentTypeInfo> punishmentTypes;
    private static List<StaffInfo> staffMembers;
    private static List<RoleInfo> rolesList;

    private TestDataProvider() {}

    public static List<PlayerInfo> getPlayers() {
        initialize();
        return Collections.unmodifiableList(players);
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }

        if (!TestDatabase.isAvailable()) {
            loadDefaults();
            initialized = true;
            return;
        }

        try {
            loadFromDatabase();
        } catch (Throwable e) {
            System.err.println("[TestDataProvider] Failed to load from DB, using defaults: " + e.getMessage());
            loadDefaults();
        }
        initialized = true;
    }

    private static void loadDefaults() {
        players = List.of(new PlayerInfo(DEFAULT_UUID, DEFAULT_USERNAME));
        playerWithPunishments = new PlayerInfo(DEFAULT_UUID, DEFAULT_USERNAME);
        playerWithoutPunishments = new PlayerInfo(DEFAULT_UUID, DEFAULT_USERNAME);
        punishmentTypes = List.of(new PunishmentTypeInfo(DEFAULT_TYPE_ORDINAL, "Default", "BAN"));
        staffMembers = List.of();
        rolesList = List.of();
    }

    private static void loadFromDatabase() {
        TestDatabase db = TestDatabase.getInstance();

        // Load players
        players = new ArrayList<>();
        playerWithPunishments = null;
        playerWithoutPunishments = null;

        try (MongoCursor<Document> cursor = db.players().find().limit(20).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                PlayerInfo info = toPlayerInfo(doc);
                if (info == null) {
                    continue;
                }

                players.add(info);

                List<Document> punishments = doc.getList("punishments", Document.class);
                if (punishments != null && !punishments.isEmpty()) {
                    if (playerWithPunishments == null) {
                        playerWithPunishments = info;
                    }
                } else {
                    if (playerWithoutPunishments == null) {
                        playerWithoutPunishments = info;
                    }
                }

                if (players.size() >= 5 && playerWithPunishments != null && playerWithoutPunishments != null) {
                    break;
                }
            }
        }

        if (players.isEmpty()) {
            players = List.of(new PlayerInfo(DEFAULT_UUID, DEFAULT_USERNAME));
        }
        if (playerWithPunishments == null) {
            playerWithPunishments = toPlayerInfo(db.players().find(elemMatch("punishments", new Document())).first());
        }
        if (playerWithoutPunishments == null) {
            playerWithoutPunishments = toPlayerInfo(db.players().find(or(exists("punishments", false), size("punishments", 0))).first());
        }
        if (playerWithPunishments == null) {
            System.err.println("[TestDataProvider] WARNING: no player with punishments found in staging DB; falling back to players.get(0) — tests requiring a punished player may be unreliable.");
            playerWithPunishments = players.get(0);
        }
        if (playerWithoutPunishments == null) {
            System.err.println("[TestDataProvider] WARNING: no player without punishments found in staging DB; falling back to players.get(0).");
            playerWithoutPunishments = players.get(0);
        }

        // Load punishment types from settings
        punishmentTypes = new ArrayList<>();
        Document punishmentSettings = db.findSettingsByType("punishment_types");
        if (punishmentSettings != null) {
            Object dataObj = punishmentSettings.get("data");
            if (dataObj instanceof Document data) {
                List<Document> types = data.getList("types", Document.class);
                if (types != null) {
                    for (Document type : types) {
                        Integer ordinal = type.getInteger("ordinal");
                        String name = type.getString("name");
                        String category = type.getString("category");
                        if (ordinal != null && name != null) {
                            punishmentTypes.add(new PunishmentTypeInfo(ordinal, name, category != null ? category : "BAN"));
                        }
                    }
                }
            }
        }
        if (punishmentTypes.isEmpty()) {
            punishmentTypes = List.of(new PunishmentTypeInfo(DEFAULT_TYPE_ORDINAL, "Default", "BAN"));
        }

        // Load staff members
        staffMembers = new ArrayList<>();
        try (MongoCursor<Document> cursor = db.staff().find().limit(10).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Object idObj = doc.get("_id");
                String id = idObj != null ? idObj.toString() : null;
                String username = doc.getString("username");
                String role = doc.getString("role");
                if (id != null && username != null) {
                    staffMembers.add(new StaffInfo(id, username, role != null ? role : ""));
                }
            }
        }

        // Load roles
        rolesList = new ArrayList<>();
        try (MongoCursor<Document> cursor = db.roles().find().limit(10).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Object idObj = doc.get("_id");
                String id = idObj != null ? idObj.toString() : null;
                String name = doc.getString("name");
                if (id != null && name != null) {
                    rolesList.add(new RoleInfo(id, name));
                }
            }
        }
    }

    // ── Public accessors ──

    private static PlayerInfo toPlayerInfo(Document doc) {
        if (doc == null) {
            return null;
        }
        String uuid = doc.getString("minecraftUuid");
        String username = lastUsername(doc.get("usernames"));
        if (uuid == null || username == null) {
            return null;
        }
        return new PlayerInfo(uuid, username);
    }

    private static String lastUsername(Object usernamesObj) {
        if (usernamesObj instanceof List<?> list && !list.isEmpty()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i) instanceof Document d) {
                    String u = d.getString("username");
                    if (u != null) {
                        return u;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Repairs corrupted (nested-array) {@code usernames} entries in the shared staging players
     * collection. This MUTATES shared staging data, so it is opt-in and must NEVER run as a side
     * effect of a read path. The query is server-filtered to only touch genuinely-corrupt documents.
     */
    public static synchronized void repairCorruptedUsernames() {
        if (!TestDatabase.isAvailable()) {
            return;
        }
        TestDatabase db = TestDatabase.getInstance();
        try (MongoCursor<Document> cursor = db.players().find(elemMatch("usernames", new Document("$type", "array"))).iterator()) {
            while (cursor.hasNext()) {
                Document player = cursor.next();
                Object usernamesObj = player.get("usernames");
                if (!(usernamesObj instanceof List<?> usernamesList)) {
                    continue;
                }

                boolean corrupted = false;
                List<Document> clean = new ArrayList<>();
                for (Object entry : usernamesList) {
                    if (entry instanceof Document doc) {
                        clean.add(doc);
                    } else if (entry instanceof List<?> nested) {
                        corrupted = true;
                        for (Object n : nested) {
                            if (n instanceof Document doc) {
                                clean.add(doc);
                            }
                        }
                    }
                }
                if (corrupted) {
                    db.players().updateOne(eq("_id", player.get("_id")), Updates.set("usernames", clean));
                }
            }
        }
    }

    public static PlayerInfo getPlayerWithPunishments() {
        initialize();
        return playerWithPunishments;
    }

    public static PlayerInfo getPlayerWithoutPunishments() {
        initialize();
        return playerWithoutPunishments;
    }

    public static List<PunishmentTypeInfo> getPunishmentTypes() {
        initialize();
        return Collections.unmodifiableList(punishmentTypes);
    }

    public static List<StaffInfo> getStaffMembers() {
        initialize();
        return Collections.unmodifiableList(staffMembers);
    }

    public static List<RoleInfo> getRoles() {
        initialize();
        return Collections.unmodifiableList(rolesList);
    }

    // ── Record types ──

    public record PlayerInfo(String uuid, String username) {}

    public record PunishmentTypeInfo(int ordinal, String name, String category) {}

    public record StaffInfo(String id, String username, String role) {}

    public record RoleInfo(String id, String name) {}
}
