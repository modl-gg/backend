package gg.modl.backend.settings.data;

import gg.modl.backend.database.CollectionName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.Map;

@Document(collection = CollectionName.SETTINGS)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Settings {
    @Id
    @Field(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(name = "type")
    private String type;

    @Field(name = "data")
    private Map<String, Object> data;
}
