package gg.modl.backend.player.data.punishment;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class Punishment {
    @NotNull
    private final String id;

    private final int type_ordinal; // Name is formatted like this to fit existing database schema

    @NotNull
    private final String issuerName;

    // TODO: migrate or add issuerId to be able to track exactly who the issuer was because names can change

    @NotNull
    private final Date issued;

    @Nullable
    private Date started;

    @NotNull
    private final List<PunishmentModification> modifications;

    @NotNull
    private final List<PunishmentNote> notes;

    @NotNull
    private final List<PunishmentEvidence> evidence;

    @NotNull
    private final List<String> attachedTicketIds;

    @Nullable
    private final Map<String, Object> data;
}
