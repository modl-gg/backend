package gg.modl.backend.knowledgebase.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgebaseCategory {
    @Id
    private String id;

    private String name;
    private String slug;
    private String description;
    private int ordinal;
    private boolean isVisible;

    private Date createdAt;
    private Date updatedAt;
}
