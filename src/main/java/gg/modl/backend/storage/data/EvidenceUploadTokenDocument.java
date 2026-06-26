package gg.modl.backend.storage.data;

import gg.modl.backend.database.CollectionName;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = CollectionName.EVIDENCE_UPLOAD_TOKENS)
public class EvidenceUploadTokenDocument {
    @Id
    private String id; // SHA-256 hex of the raw token
    private String serverDatabaseName;
    private String punishmentId;
    private String playerUuid;
    private String issuerName;
    private Instant createdAt;
    // TTL index on this field is created explicitly in MongoIndexBootstrapService (auto-index-creation
    // is disabled), so @Indexed here would not create one.
    private Instant expiresAt;

    public EvidenceUploadTokenDocument(
        String id,
        String serverDatabaseName,
        String punishmentId,
        String playerUuid,
        String issuerName,
        Instant createdAt,
        Instant expiresAt
    ) {
        this.id = id;
        this.serverDatabaseName = serverDatabaseName;
        this.punishmentId = punishmentId;
        this.playerUuid = playerUuid;
        this.issuerName = issuerName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
