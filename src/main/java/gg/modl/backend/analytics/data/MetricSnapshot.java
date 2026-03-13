package gg.modl.backend.analytics.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = CollectionName.METRIC_SNAPSHOTS)
@Data
@GenerateMongoFields
public class MetricSnapshot {
    @Id
    private String id;

    @Field("date")
    private Date date; // truncated to hour (UTC)

    @Field("activeServers")
    private long activeServers;

    @Field("totalServers")
    private long totalServers;

    @Field("totalPlayers")
    private long totalPlayers;

    @Field("onlinePlayers")
    private long onlinePlayers;

    @Field("createdAt")
    private Date createdAt;
}
