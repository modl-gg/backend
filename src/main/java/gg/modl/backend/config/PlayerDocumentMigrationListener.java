package gg.modl.backend.config;

import gg.modl.backend.player.data.Player;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlayerDocumentMigrationListener extends AbstractMongoEventListener<Player> {

    @Override
    public void onAfterLoad(AfterLoadEvent<Player> event) {
        Document doc = event.getSource();
        migrateIpList(doc);
        migrateDataFields(doc);
        migratePendingNotifications(doc);
    }

    private void migrateIpList(Document doc) {
        List<?> ipAddresses = doc.getList("ipAddresses", Document.class);
        if (ipAddresses != null && !ipAddresses.isEmpty()) {
            return;
        }

        List<?> ipList = doc.getList("ipList", Document.class);
        if (ipList != null && !ipList.isEmpty()) {
            doc.put("ipAddresses", ipList);
        }
    }

    private void migrateDataFields(Document doc) {
        Document data = doc.get("data", Document.class);
        if (data == null) {
            return;
        }

        if (!data.containsKey("totalPlaytimeSeconds") && data.containsKey("totalPlaytime")) {
            data.put("totalPlaytimeSeconds", data.get("totalPlaytime"));
        }

        if (!data.containsKey("lastLinkedUpdate") && data.containsKey("lastLinkedAccountUpdate")) {
            data.put("lastLinkedUpdate", data.get("lastLinkedAccountUpdate"));
        }
    }

    private void migratePendingNotifications(Document doc) {
        Document data = doc.get("data", Document.class);
        List<?> rootNotifications = doc.getList("pendingNotifications", Document.class);

        if (rootNotifications == null || rootNotifications.isEmpty()) {
            return;
        }

        if (data == null) {
            data = new Document();
            doc.put("data", data);
        }

        if (!data.containsKey("pendingNotifications")) {
            data.put("pendingNotifications", rootNotifications);
        }
    }
}
