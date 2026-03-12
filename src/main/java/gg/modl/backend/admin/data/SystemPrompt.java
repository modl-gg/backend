package gg.modl.backend.admin.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "systemprompts")
@GenerateMongoFields
public class SystemPrompt {
    @Id
    private String id;
    private String prompt;
    private boolean isActive = true;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
}
