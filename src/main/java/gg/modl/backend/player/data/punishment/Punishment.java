package gg.modl.backend.player.data.punishment;

import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;
import gg.modl.backend.database.mongo.codegen.MongoFieldAlias;
import gg.modl.backend.database.mongo.codegen.MongoFieldAliases;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    @MongoFieldAlias(name = "DATA_LINKED_BAN_PARENT_UUID", path = "data.linkedBanParentUuid"),
    @MongoFieldAlias(name = "DATA_STATUS", path = "data.status"),
    @MongoFieldAlias(name = "DATA_REASON", path = "data.reason"),
    @MongoFieldAlias(name = "DATA_DURATION", path = "data.duration"),
    @MongoFieldAlias(name = "DATA_SEVERITY", path = "data.severity"),
    @MongoFieldAlias(name = "DATA_ALT_BLOCKING", path = "data.altBlocking"),
    @MongoFieldAlias(name = "DATA_WIPE_AFTER_EXPIRY", path = "data.wipeAfterExpiry"),
    @MongoFieldAlias(name = "DATA_STAT_WIPE_COMPLETED", path = "data.statWipeCompleted"),
    @MongoFieldAlias(name = "DATA_BLOCKED_NAME", path = "data.blockedName"),
    @MongoFieldAlias(name = "DATA_BLOCKED_SKIN", path = "data.blockedSkin"),
    @MongoFieldAlias(name = "DATA_OFFENSE_LEVEL", path = "data.offenseLevel")
})
public class Punishment {
    public static final int LINKED_BAN_TYPE_ORDINAL = 4;

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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object> data = new HashMap<>();

    public PunishmentDataView data() {
        return PunishmentDataView.ownedBy(data, map -> data = map);
    }

    public void replaceData(Map<String, Object> replacement) {
        this.data = replacement;
    }

    public boolean isPardoned() {
        return modifications.stream()
            .anyMatch(modification -> PunishmentModificationType.isPardon(modification.type()));
    }

    public boolean isUnstarted() {
        if (!data().isUnstarted()) {
            return false;
        }
        return modifications.stream()
            .noneMatch(modification -> PunishmentModificationType.isPardon(modification.type()));
    }

    public boolean hasUnstartedStatus() {
        return data().isUnstarted();
    }
}
