package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import java.util.List;

public final class TempUploadKeys {
    static final String NEW_ENTITY_ID = "new";

    private TempUploadKeys() {
    }

    static List<String> prefixes(Server server) {
        return prefixes(server.getDatabaseName());
    }

    static List<String> prefixes(String databaseName) {
        return List.of(
            databaseName + "/tickets/" + NEW_ENTITY_ID + "/",
            databaseName + "/appeal/" + NEW_ENTITY_ID + "/"
        );
    }

    public static boolean isAnonymousTempEntity(String entityId) {
        return entityId != null && NEW_ENTITY_ID.equalsIgnoreCase(entityId.trim());
    }
}
