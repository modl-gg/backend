package gg.modl.backend.replaylite.data;

import gg.modl.backend.database.CollectionName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = CollectionName.REPLAY_LITE_REPLAYS)
public class ReplayLiteDocument {
    @Id
    private String id;
    private UUID pluginServerUuid;
    private String objectKey;
    private ReplayLiteStatus status;
    private long requestedSize;
    private Long confirmedSize;
    private String mcVersion;
    private List<ReplayLiteLabel> labels;
    private Instant createdAt;
    private Instant confirmedAt;
    private Instant expiresAt;
    private Instant labeledAt;
    private String uploadInitIp;
    private String confirmIp;
    private String labelIp;
}
