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

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Punishment {
    @NotNull
    @Field("_id")
    private String id;

    @Field("type_ordinal")
    private int type_ordinal; // Name is formatted like this to fit existing database schema

    @NotNull
    private String issuerName;

    // TODO: migrate or add issuerId to be able to track exactly who the issuer was because names can change

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
