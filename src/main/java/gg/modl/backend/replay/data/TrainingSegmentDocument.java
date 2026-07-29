package gg.modl.backend.replay.data;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.Binary;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document
@GenerateMongoFields
public class TrainingSegmentDocument {
    @Id
    private String id;
    private String replayId;
    private String serverName;
    private String serverDatabaseName;
    private String playerUuid;
    private String playerName;
    private String verdict;
    private String cheatType;
    private int confidence;
    private String notes;
    private long startMs;
    private long endMs;
    private String mcVersion;
    @ToString.Exclude
    private Binary segmentBinary;
    private Date createdAt;
}
