package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.player.data.punishment.Punishment;

public final class PunishmentFields {
    public static final MongoField<Punishment> ID = MongoFieldNames.field(Punishment.class, Punishment::getId);
    public static final MongoField<Punishment> TYPE_ORDINAL = MongoFieldNames.field(Punishment.class, Punishment::getTypeOrdinal);
    public static final MongoField<Punishment> DATA = MongoFieldNames.field(Punishment.class, Punishment::getData);
    public static final MongoField<Punishment> DATA_LINKED_BAN_ID = MongoFieldNames.raw(Punishment.class, DATA.path() + ".linkedBanId");

    private PunishmentFields() {
    }
}
