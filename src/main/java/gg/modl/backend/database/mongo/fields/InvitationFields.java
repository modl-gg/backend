package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.staff.data.Invitation;

public final class InvitationFields {
    public static final MongoField<Invitation> ID = MongoFieldNames.field(Invitation.class, Invitation::getId);
    public static final MongoField<Invitation> EMAIL = MongoFieldNames.field(Invitation.class, Invitation::getEmail);
    public static final MongoField<Invitation> TOKEN = MongoFieldNames.field(Invitation.class, Invitation::getToken);
    public static final MongoField<Invitation> EXPIRES_AT = MongoFieldNames.field(Invitation.class, Invitation::getExpiresAt);
    public static final MongoField<Invitation> CREATED_AT = MongoFieldNames.field(Invitation.class, Invitation::getCreatedAt);
    public static final MongoField<Invitation> UPDATED_AT = MongoFieldNames.field(Invitation.class, Invitation::getUpdatedAt);

    private InvitationFields() {
    }
}
