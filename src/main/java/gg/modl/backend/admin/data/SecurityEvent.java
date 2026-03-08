package gg.modl.backend.admin.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "security_events")
@GenerateMongoFields
public class SecurityEvent {
    @Id
    private String id;
    private String type;
    private String severity;
    private String source;
    private String description;
    private Date timestamp;
}
