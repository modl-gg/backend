package gg.modl.backend.admin.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "system_logs")
@GenerateMongoFields
public class SystemLog {
    @Id
    private String id;
    private String level; // critical, error, warning, info, debug
    private String message;
    private String source;
    private String category;
    private String serverId;
    private Map<String, Object> metadata;
    private boolean resolved;
    private String resolvedBy;
    private Date resolvedAt;
    private Date timestamp;

    public SystemLog(String level, String message, String source) {
        this();
        this.level = level;
        this.message = message;
        this.source = source;
    }

    public SystemLog() {
        this.timestamp = new Date();
        this.resolved = false;
    }
}
