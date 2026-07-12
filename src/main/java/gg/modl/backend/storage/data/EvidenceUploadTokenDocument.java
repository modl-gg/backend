package gg.modl.backend.storage.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@GenerateMongoFields
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
}
