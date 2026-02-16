package gg.modl.backend.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public final class TestDatabase {

    private static volatile TestDatabase instance;

    private final MongoClient client;
    private final MongoDatabase serverDb;

    private TestDatabase(String uri, String domain) {
        this.client = MongoClients.create(uri);
        // Extract subdomain from full domain (e.g. "byteful.modl.gg" -> "byteful")
        String subdomain = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        this.serverDb = client.getDatabase("server_" + subdomain);
    }

    public static TestDatabase getInstance() {
        if (instance == null) {
            synchronized (TestDatabase.class) {
                if (instance == null) {
                    String uri = StagingCredentials.mongoUri();
                    String domain = StagingCredentials.serverDomain();
                    instance = new TestDatabase(uri, domain);
                }
            }
        }
        return instance;
    }

    public static boolean isAvailable() {
        String uri = StagingCredentials.mongoUri();
        return uri != null && !uri.isBlank();
    }

    // ── Collection accessors ──

    public MongoCollection<Document> players()       { return serverDb.getCollection("players"); }
    public MongoCollection<Document> tickets()       { return serverDb.getCollection("tickets"); }
    public MongoCollection<Document> staff()         { return serverDb.getCollection("staffs"); }
    public MongoCollection<Document> roles()         { return serverDb.getCollection("staffroles"); }
    public MongoCollection<Document> settings()      { return serverDb.getCollection("settings"); }
    public MongoCollection<Document> homepageCards() { return serverDb.getCollection("homepagecards"); }
    public MongoCollection<Document> kbCategories()  { return serverDb.getCollection("knowledgebasecategories"); }
    public MongoCollection<Document> kbArticles()    { return serverDb.getCollection("knowledgebasearticles"); }

    // ── Query helpers ──

    public Document findPlayerByUuid(String uuid) {
        return players().find(eq("minecraftUuid", uuid)).first();
    }

    public Document findPunishmentInPlayer(String playerUuid, String punishmentId) {
        Document player = findPlayerByUuid(playerUuid);
        if (player == null) return null;
        List<Document> punishments = player.getList("punishments", Document.class);
        if (punishments == null) return null;
        for (Document p : punishments) {
            String id = null;
            Object idObj = p.get("_id");
            if (idObj instanceof ObjectId oid) {
                id = oid.toHexString();
            } else if (idObj instanceof String s) {
                id = s;
            }
            // Also check the "id" field
            if (punishmentId.equals(id) || punishmentId.equals(p.getString("id"))) {
                return p;
            }
        }
        return null;
    }

    public Document findTicketById(String ticketId) {
        // Try string _id first, then ObjectId
        Document doc = tickets().find(eq("_id", ticketId)).first();
        if (doc == null) {
            try {
                doc = tickets().find(eq("_id", new ObjectId(ticketId))).first();
            } catch (IllegalArgumentException ignored) {}
        }
        return doc;
    }

    public Document findStaffByUsername(String username) {
        return staff().find(eq("username", username)).first();
    }

    public Document findRoleById(String roleId) {
        Document doc = roles().find(eq("_id", roleId)).first();
        if (doc == null) {
            try {
                doc = roles().find(eq("_id", new ObjectId(roleId))).first();
            } catch (IllegalArgumentException ignored) {}
        }
        if (doc == null) {
            doc = roles().find(eq("id", roleId)).first();
        }
        return doc;
    }

    public Document findSettingsByType(String type) {
        return settings().find(eq("type", type)).first();
    }

    public Document findHomepageCardById(String cardId) {
        Document doc = homepageCards().find(eq("_id", cardId)).first();
        if (doc == null) {
            try {
                doc = homepageCards().find(eq("_id", new ObjectId(cardId))).first();
            } catch (IllegalArgumentException ignored) {}
        }
        return doc;
    }

    public Document findKbCategoryById(String categoryId) {
        Document doc = kbCategories().find(eq("_id", categoryId)).first();
        if (doc == null) {
            try {
                doc = kbCategories().find(eq("_id", new ObjectId(categoryId))).first();
            } catch (IllegalArgumentException ignored) {}
        }
        return doc;
    }

    public Document findKbArticleById(String articleId) {
        Document doc = kbArticles().find(eq("_id", articleId)).first();
        if (doc == null) {
            try {
                doc = kbArticles().find(eq("_id", new ObjectId(articleId))).first();
            } catch (IllegalArgumentException ignored) {}
        }
        return doc;
    }

    public void close() {
        client.close();
        synchronized (TestDatabase.class) {
            instance = null;
        }
    }
}
