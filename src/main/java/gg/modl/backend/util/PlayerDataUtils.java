package gg.modl.backend.util;

import gg.modl.backend.database.mongo.fields.PlayerFields;
import gg.modl.backend.player.data.UsernameEntry;
import lombok.experimental.UtilityClass;
import org.bson.Document;

import java.util.List;
import java.util.Map;

@UtilityClass
public class PlayerDataUtils {

    public String extractLatestUsername(List<UsernameEntry> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return "Unknown";
        }
        return usernames.get(usernames.size() - 1).username();
    }

    public String extractLatestUsername(Object usernamesValue) {
        if (!(usernamesValue instanceof List<?> usernames) || usernames.isEmpty()) {
            return "Unknown";
        }

        Object lastEntry = usernames.get(usernames.size() - 1);
        if (lastEntry instanceof Document entryDocument) {
            Object username = entryDocument.get("username");
            if (username != null && !String.valueOf(username).isBlank()) {
                return String.valueOf(username);
            }
        } else if (lastEntry instanceof Map<?, ?> entryMap) {
            Object username = entryMap.get("username");
            if (username != null && !String.valueOf(username).isBlank()) {
                return String.valueOf(username);
            }
        }

        return "Unknown";
    }

    public String extractMinecraftUuid(Document row) {
        Object value = row.get(PlayerFields.MINECRAFT_UUID);
        return value != null ? String.valueOf(value) : "";
    }
}
