package gg.modl.backend.settings.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.Date;

@Document(collection = CollectionName.SETTINGS)
@Data
@NoArgsConstructor
@AllArgsConstructor
@GenerateMongoFields
public class Settings {
    @Id
    @Field(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(name = "type")
    private String type;

    @Field(name = "data")
    private Object data;

    @Field(name = "version")
    private Long version;

    @Field(name = "updatedAt", targetType = FieldType.DATE_TIME)
    private Date updatedAt;

    public Settings(String id, String type, Object data) {
        this(id, type, data, null, null);
    }
}
