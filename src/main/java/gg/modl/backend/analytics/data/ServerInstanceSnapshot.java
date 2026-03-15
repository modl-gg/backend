package gg.modl.backend.analytics.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = CollectionName.SERVER_INSTANCE_SNAPSHOTS)
@Data
@GenerateMongoFields
public class ServerInstanceSnapshot {
    @Id
    private String id;

    @Field
    private Date date; // truncated to 5-min boundary

    @Field
    private List<ServerEntry> servers;

    @Field
    private Date createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerEntry {
        private String serverId;
        private String serverName;
        private int playerCount;
        private String platform; // "spigot", "velocity", "bungee"
        private String version;  // e.g. "1.21", "3.3.0"
        private String ipAddress;
        private String pluginVersion; // modl plugin version e.g. "2.0.5"
    }
}
