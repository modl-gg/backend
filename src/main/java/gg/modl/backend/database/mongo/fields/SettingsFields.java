package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.settings.data.Settings;

public final class SettingsFields {
    public static final MongoField<Settings> ID = MongoFieldNames.field(Settings.class, Settings::getId);
    public static final MongoField<Settings> TYPE = MongoFieldNames.field(Settings.class, Settings::getType);
    public static final MongoField<Settings> DATA = MongoFieldNames.field(Settings.class, Settings::getData);
    public static final MongoField<Settings> VERSION = MongoFieldNames.field(Settings.class, Settings::getVersion);
    public static final MongoField<Settings> UPDATED_AT = MongoFieldNames.field(Settings.class, Settings::getUpdatedAt);

    private SettingsFields() {
    }
}
