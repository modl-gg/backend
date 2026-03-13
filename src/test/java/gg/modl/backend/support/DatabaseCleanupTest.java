package gg.modl.backend.support;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * One-time cleanup: fix corrupted usernames arrays where test runs
 * inserted nested Lists instead of flat Documents.
 */
class DatabaseCleanupTest {

    @Test
    void fixCorruptedUsernames() {
        Assumptions.assumeTrue(TestDatabase.isAvailable(), "DB not available");
        var db = TestDatabase.getInstance();

        int fixed = 0;
        try (MongoCursor<Document> cursor = db.players().find().iterator()) {
            while (cursor.hasNext()) {
                Document player = cursor.next();
                Object usernamesObj = player.get("usernames");
                if (!(usernamesObj instanceof List<?> usernamesList)) {
                    continue;
                }

                boolean corrupted = false;
                List<Document> cleanUsernames = new ArrayList<>();

                for (Object entry : usernamesList) {
                    if (entry instanceof Document doc) {
                        cleanUsernames.add(doc);
                    } else if (entry instanceof List<?> nestedList) {
                        // Corrupted: extract Documents from nested list
                        corrupted = true;
                        for (Object nested : nestedList) {
                            if (nested instanceof Document doc) {
                                cleanUsernames.add(doc);
                            }
                        }
                    }
                }

                if (corrupted) {
                    String uuid = player.getString("minecraftUuid");
                    System.out.println("Fixing corrupted usernames for player: " + uuid);
                    db.players().updateOne(
                        eq("_id", player.get("_id")),
                        Updates.set("usernames", cleanUsernames)
                    );
                    fixed++;
                }
            }
        }

        System.out.println("Fixed " + fixed + " player(s) with corrupted usernames");
    }
}
