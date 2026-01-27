package gg.modl.backend.knowledgebase.data;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class KnowledgebaseArticle {
    @Id
    private String id;

    private String title;
    private String slug;
    private String content;
    private String categoryId;
    private int ordinal;

    @JsonProperty("is_visible")
    private boolean isVisible;

    @JsonProperty("created_at")
    private Date createdAt;

    @JsonProperty("updated_at")
    private Date updatedAt;
}
