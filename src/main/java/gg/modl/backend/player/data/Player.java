package gg.modl.backend.player.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.player.data.punishment.Punishment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = CollectionName.PLAYERS)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {
    @Id
    @Field(targetType = FieldType.STRING)
    private String id;

    @Field(name = "minecraftUuid", targetType = FieldType.STRING)
    @Indexed(name = "minecraftUuid_1", unique = true, sparse = true)
    private UUID minecraftUuid;

    @Field(name = "usernames", targetType = FieldType.ARRAY)
    @Builder.Default
    private List<UsernameEntry> usernames = new ArrayList<>();

    @Field(name = "notes", targetType = FieldType.ARRAY)
    @Builder.Default
    private List<NoteEntry> notes = new ArrayList<>();

    @Field(name = "ipAddresses", targetType = FieldType.ARRAY)
    @Builder.Default
    private List<IPEntry> ipAddresses = new ArrayList<>();

    @Field(name = "punishments", targetType = FieldType.ARRAY)
    @Builder.Default
    private List<Punishment> punishments = new ArrayList<>();

    @Field(name = "data")
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();
}
