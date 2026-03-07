package gg.modl.backend.player.data.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
    private List<PunishmentModification> modifications;

    @NotNull
    private List<PunishmentNote> notes;

    @NotNull
    private List<PunishmentEvidence> evidence;

    @NotNull
    private List<String> attachedTicketIds;

    @Nullable
    private Map<String, Object> data;
}
