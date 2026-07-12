package gg.modl.backend.beta.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "beta_audit")
@GenerateMongoFields
public class BetaAudit {
    @Id
    private String id;

    @Field("action")
    private String action;

    @Field("serverId")
    private String serverId;

    @Field("adminEmail")
    private String adminEmail;

    @Field("timestamp")
    private Date timestamp;

    @Field("details")
    private String details;
}
