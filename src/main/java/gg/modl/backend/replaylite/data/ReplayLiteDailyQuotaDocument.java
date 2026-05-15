package gg.modl.backend.replaylite.data;

import gg.modl.backend.database.CollectionName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = CollectionName.REPLAY_LITE_DAILY_QUOTAS)
public class ReplayLiteDailyQuotaDocument {
    @Id
    private String id;
    private UUID pluginServerUuid;
    private LocalDate day;
    private int count;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
}
