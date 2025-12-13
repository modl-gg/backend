package gg.modl.backend.player.data;

import gg.modl.backend.database.CollectionName;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.List;
import java.util.UUID;

@Document(collection = CollectionName.PLAYERS)
@Data
@RequiredArgsConstructor
public class Player {
    @Id
    @Field(targetType = FieldType.STRING)
    private final String id;

    @Field(name = "minecraftUuid", targetType = FieldType.STRING)
    @Indexed(unique = true, sparse = true)
    private final UUID minecraftUuid;

    @Field(name = "usernames", targetType = FieldType.ARRAY)
    private final List<UsernameEntry> usernames;

    @Field(name = "notes", targetType = FieldType.ARRAY)
    private final List<NoteEntry> notes;


}
