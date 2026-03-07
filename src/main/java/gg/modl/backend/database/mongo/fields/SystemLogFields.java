package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.admin.data.SystemLog;
import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;

public final class SystemLogFields {
    public static final MongoField<SystemLog> ID = MongoFieldNames.field(SystemLog.class, SystemLog::getId);
    public static final MongoField<SystemLog> LEVEL = MongoFieldNames.field(SystemLog.class, SystemLog::getLevel);
    public static final MongoField<SystemLog> MESSAGE = MongoFieldNames.field(SystemLog.class, SystemLog::getMessage);
    public static final MongoField<SystemLog> SOURCE = MongoFieldNames.field(SystemLog.class, SystemLog::getSource);
    public static final MongoField<SystemLog> CATEGORY = MongoFieldNames.field(SystemLog.class, SystemLog::getCategory);
    public static final MongoField<SystemLog> SERVER_ID = MongoFieldNames.field(SystemLog.class, SystemLog::getServerId);
    public static final MongoField<SystemLog> RESOLVED = MongoFieldNames.field(SystemLog.class, SystemLog::isResolved);
    public static final MongoField<SystemLog> RESOLVED_BY = MongoFieldNames.field(SystemLog.class, SystemLog::getResolvedBy);
    public static final MongoField<SystemLog> RESOLVED_AT = MongoFieldNames.field(SystemLog.class, SystemLog::getResolvedAt);
    public static final MongoField<SystemLog> TIMESTAMP = MongoFieldNames.field(SystemLog.class, SystemLog::getTimestamp);

    private SystemLogFields() {
    }
}
