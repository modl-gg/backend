package gg.modl.backend.analytics.data;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Document(collection = "metric_snapshots")
@Data
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
