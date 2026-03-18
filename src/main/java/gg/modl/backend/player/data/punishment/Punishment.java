package gg.modl.backend.player.data.punishment;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import gg.modl.backend.database.mongo.codegen.MongoFieldAlias;
import gg.modl.backend.database.mongo.codegen.MongoFieldAliases;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@GenerateMongoFields
@MongoFieldAliases({
    @MongoFieldAlias(name = "DATA_LINKED_BAN_ID", path = "data.linkedBanId"),
    @MongoFieldAlias(name = "DATA_STATUS", path = "data.status"),
    @MongoFieldAlias(name = "DATA_REASON", path = "data.reason"),
    @MongoFieldAlias(name = "DATA_DURATION", path = "data.duration")
})
public class Punishment {
    @NotNull
    @Field(value = "id", targetType = FieldType.STRING)
    private String id;

    @Field("typeOrdinal")
    private int typeOrdinal;

    @Nullable
    private String issuerName;

    @Nullable
    private String issuerId;

    @NotNull
    private Date issued;

    @Nullable
    private Date started;

    @NotNull
    private List<PunishmentModification> modifications = new ArrayList<>();

    @NotNull
    private List<PunishmentNote> notes = new ArrayList<>();

    @NotNull
    private List<PunishmentEvidence> evidence = new ArrayList<>();

    @NotNull
    private List<String> attachedTicketIds = new ArrayList<>();

    @Nullable
    private Map<String, Object> data = new HashMap<>();
}
