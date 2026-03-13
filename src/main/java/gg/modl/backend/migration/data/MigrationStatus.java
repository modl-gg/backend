package gg.modl.backend.migration.data;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "migrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationStatus {
    @Id
    private String id;

    @Field("taskId")
    private String taskId;
    @Field("type")
    private String type;
    @Field("status")
    private String status;
    @Field("progress")
    private MigrationProgress progress;
    @Field("startedAt")
    private Date startedAt;
    @Field("completedAt")
    private Date completedAt;
    @Field("error")
    private String error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MigrationProgress {
        @Field("message")
        private String message;
        @Field("recordsProcessed")
        private Integer recordsProcessed;
        @Field("recordsSkipped")
        private Integer recordsSkipped;
        @Field("totalRecords")
        private Integer totalRecords;
    }
}
