package gg.modl.backend.migration.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "migrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationStatus {
    @Id
    private String id;

    private String taskId;
    private String type;
    private String status;
    private MigrationProgress progress;
    private Date startedAt;
    private Date completedAt;
    private String error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MigrationProgress {
        private String message;
        private Integer recordsProcessed;
        private Integer recordsSkipped;
        private Integer totalRecords;
    }
}
