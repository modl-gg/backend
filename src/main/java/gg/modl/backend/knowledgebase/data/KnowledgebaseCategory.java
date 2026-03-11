package gg.modl.backend.knowledgebase.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = CollectionName.KNOWLEDGEBASE_CATEGORIES)
@GenerateMongoFields
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
