package gg.modl.backend.support;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DebugDataTest {

    @Test
    void printPlayerData() {
        Assumptions.assumeTrue(TestDatabase.isAvailable(), "DB not available");
        var db = TestDatabase.getInstance();

        // Print first 3 players
        var cursor = db.players().find().limit(3).iterator();
        int i = 0;
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String uuid = doc.getString("minecraftUuid");
            Object usernamesObj = doc.get("usernames");
            Object usernameField = doc.get("username");
            List<Document> punishments = doc.getList("punishments", Document.class);
            int punishmentCount = punishments != null ? punishments.size() : 0;

            System.out.println("=== Player " + (i++) + " ===");
            System.out.println("  uuid: " + uuid);
            System.out.println("  username field: " + usernameField);
            System.out.println("  usernames: " + usernamesObj);
            System.out.println("  punishments count: " + punishmentCount);

            if (punishments != null && !punishments.isEmpty()) {
                Document p = punishments.get(0);
                System.out.println("  first punishment keys: " + p.keySet());
                System.out.println("  first punishment issuerName: " + p.getString("issuerName"));
                System.out.println("  first punishment type_ordinal: " + p.get("type_ordinal"));
                System.out.println("  first punishment modifications: " + p.get("modifications"));
                System.out.println("  first punishment notes: " + p.get("notes"));
                System.out.println("  first punishment evidence: " + p.get("evidence"));
                System.out.println("  first punishment attachedTicketIds: " + p.get("attachedTicketIds"));
            }
        }
        cursor.close();

        // Print what TestDataProvider loads
        var players = TestDataProvider.getPlayers();
        System.out.println("\n=== TestDataProvider loaded " + players.size() + " players ===");
        for (var p : players) {
            System.out.println("  " + p.uuid() + " -> " + p.username());
        }

        // Test a direct API call with the loaded UUID
        Assumptions.assumeTrue(StagingCredentials.isAvailable(), "Staging not available");
        var api = new ApiClient();
        try {
            String testUuid = players.get(0).uuid();
            System.out.println("\n=== Testing API with UUID: " + testUuid + " ===");

            var mcResponse = api.minecraftGet("/v1/minecraft/players/" + testUuid);
            System.out.println("Minecraft GET player: " + mcResponse.statusCode());
            if (mcResponse.statusCode() != 200) {
                System.out.println("  body: " + mcResponse.body());
            }

            var panelResponse = api.panelGet("/v1/panel/players/" + testUuid);
            System.out.println("Panel GET player: " + panelResponse.statusCode());
            if (panelResponse.statusCode() != 200) {
                System.out.println("  body: " + panelResponse.body());
            }

            var searchResponse = api.panelGet("/v1/panel/players/punishments/search?q=test&activeOnly=false");
            System.out.println("Panel search punishments: " + searchResponse.statusCode());
            if (searchResponse.statusCode() != 200) {
                System.out.println("  body: " + searchResponse.body());
            }
        } catch (Exception e) {
            System.out.println("API error: " + e.getMessage());
        }
    }
}
