package gg.modl.backend.admin.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "security_events")
@GenerateMongoFields
public class SecurityEvent {
    @Id
    private String id;
    @Field("type")
    private String type;
    @Field("severity")
    private String severity;
    @Field("source")
    private String source;
    @Field("description")
    private String description;
    @Field("timestamp")
    private Date timestamp;
}
