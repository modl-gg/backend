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
    private String tokenHash;
    private String serverDatabaseName;
    private String punishmentId;
    private String playerUuid;
    private String issuerName;
    private Instant createdAt;
    private Instant expiresAt;

    public EvidenceUploadTokenDocument(
        String tokenHash,
        String serverDatabaseName,
        String punishmentId,
        String playerUuid,
        String issuerName,
        Instant createdAt,
        Instant expiresAt
    ) {
        this.tokenHash = tokenHash;
        this.serverDatabaseName = serverDatabaseName;
        this.punishmentId = punishmentId;
        this.playerUuid = playerUuid;
        this.issuerName = issuerName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
