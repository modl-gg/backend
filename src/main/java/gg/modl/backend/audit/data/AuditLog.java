package gg.modl.backend.audit.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import gg.modl.backend.database.mongo.codegen.MongoFieldAlias;
import gg.modl.backend.database.mongo.codegen.MongoFieldAliases;
import java.util.Date;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateMongoFields
@MongoFieldAliases({
    @MongoFieldAlias(name = "METADATA_ROLLED_BACK", path = "metadata.rolledBack"),
    @MongoFieldAlias(name = "METADATA_ROLLBACK_DATE", path = "metadata.rollbackDate"),
    @MongoFieldAlias(name = "METADATA_ROLLBACK_BY", path = "metadata.rollbackBy"),
    @MongoFieldAlias(name = "METADATA_CAN_ROLLBACK", path = "metadata.canRollback")
})
public class AuditLog {
    @Id
    private String id;

    private Date created;
    private String level;
    private String source;
    private String description;
    private Map<String, Object> metadata;
}
