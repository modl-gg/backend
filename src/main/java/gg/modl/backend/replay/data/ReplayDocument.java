package gg.modl.backend.replay.data;

import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document
public class ReplayDocument {
    @Id
    private String id;
    private String mcVersion;
    private long fileSize;
    private String storageKey;
    private String status;
    private Date createdAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_FAILED = "FAILED";
}
