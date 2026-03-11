package gg.modl.backend.log.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@GenerateMongoFields(className = "ServerLogFields")
public class SystemLog {
    @Id
    private String id;

    private String description;

    @Builder.Default
    private String level = "info";

    @Builder.Default
    private String source = "system";

    private Date created;
}
