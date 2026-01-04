package gg.modl.backend.log.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
