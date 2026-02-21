package gg.modl.backend.admin.data;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "systemprompts")
public class SystemPrompt {
    @Id
    private String id;
    private StrictnessLevel strictnessLevel;
    private String prompt;
    private boolean isActive = true;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    public enum StrictnessLevel {
        LENIENT,
        STANDARD,
        STRICT;
    }
}
