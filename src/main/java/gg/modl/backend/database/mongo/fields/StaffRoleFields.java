package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.role.data.StaffRole;

public final class StaffRoleFields {
    public static final MongoField<StaffRole> ID = MongoFieldNames.field(StaffRole.class, StaffRole::getId);
    public static final MongoField<StaffRole> NAME = MongoFieldNames.field(StaffRole.class, StaffRole::getName);
    public static final MongoField<StaffRole> DESCRIPTION = MongoFieldNames.field(StaffRole.class, StaffRole::getDescription);
    public static final MongoField<StaffRole> PERMISSIONS = MongoFieldNames.field(StaffRole.class, StaffRole::getPermissions);
    public static final MongoField<StaffRole> IS_DEFAULT = MongoFieldNames.field(StaffRole.class, StaffRole::isDefault);
    public static final MongoField<StaffRole> ORDER = MongoFieldNames.field(StaffRole.class, StaffRole::getOrder);
    public static final MongoField<StaffRole> CREATED_AT = MongoFieldNames.field(StaffRole.class, StaffRole::getCreatedAt);
    public static final MongoField<StaffRole> UPDATED_AT = MongoFieldNames.field(StaffRole.class, StaffRole::getUpdatedAt);

    private StaffRoleFields() {
    }
}
