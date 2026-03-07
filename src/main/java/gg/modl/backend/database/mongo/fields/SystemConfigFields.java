package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.admin.data.SystemConfig;
import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;

public final class SystemConfigFields {
    public static final MongoField<SystemConfig> ID = MongoFieldNames.field(SystemConfig.class, SystemConfig::getId);
    public static final MongoField<SystemConfig> CONFIG_ID = MongoFieldNames.field(SystemConfig.class, SystemConfig::getConfigId);
    public static final MongoField<SystemConfig> CREATED_AT = MongoFieldNames.field(SystemConfig.class, SystemConfig::getCreatedAt);
    public static final MongoField<SystemConfig> UPDATED_AT = MongoFieldNames.field(SystemConfig.class, SystemConfig::getUpdatedAt);

    private SystemConfigFields() {
    }
}
