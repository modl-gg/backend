package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.staff.data.Staff;

public final class StaffFields {
    public static final MongoField<Staff> ID = MongoFieldNames.field(Staff.class, Staff::getId);
    public static final MongoField<Staff> EMAIL = MongoFieldNames.field(Staff.class, Staff::getEmail);
    public static final MongoField<Staff> USERNAME = MongoFieldNames.field(Staff.class, Staff::getUsername);
    public static final MongoField<Staff> ROLE = MongoFieldNames.field(Staff.class, Staff::getRole);
    public static final MongoField<Staff> ASSIGNED_MINECRAFT_UUID = MongoFieldNames.field(Staff.class, Staff::getAssignedMinecraftUuid);
    public static final MongoField<Staff> ASSIGNED_MINECRAFT_USERNAME = MongoFieldNames.field(Staff.class, Staff::getAssignedMinecraftUsername);
    public static final MongoField<Staff> SUBSCRIBED_TICKETS = MongoFieldNames.field(Staff.class, Staff::getSubscribedTickets);
    public static final MongoField<Staff> TICKET_SUBSCRIPTION_SETTINGS = MongoFieldNames.field(Staff.class, Staff::getTicketSubscriptionSettings);
    public static final MongoField<Staff> TWO_FACTOR_TOKEN = MongoFieldNames.field(Staff.class, Staff::getTwoFactorToken);
    public static final MongoField<Staff> TWO_FACTOR_TOKEN_IP = MongoFieldNames.field(Staff.class, Staff::getTwoFactorTokenIp);
    public static final MongoField<Staff> TWO_FACTOR_TOKEN_CREATED_AT = MongoFieldNames.field(Staff.class, Staff::getTwoFactorTokenCreatedAt);
    public static final MongoField<Staff> TWO_FACTOR_PENDING_DELIVERY = MongoFieldNames.field(Staff.class, Staff::isTwoFactorPendingDelivery);
    public static final MongoField<Staff> TWO_FACTOR_SESSION_IP = MongoFieldNames.field(Staff.class, Staff::getTwoFactorSessionIp);
    public static final MongoField<Staff> TWO_FACTOR_SESSION_EXPIRES_AT = MongoFieldNames.field(Staff.class, Staff::getTwoFactorSessionExpiresAt);
    public static final MongoField<Staff> LAST_SEEN = MongoFieldNames.field(Staff.class, Staff::getLastSeen);
    public static final MongoField<Staff> CREATED_AT = MongoFieldNames.field(Staff.class, Staff::getCreatedAt);
    public static final MongoField<Staff> UPDATED_AT = MongoFieldNames.field(Staff.class, Staff::getUpdatedAt);

    private StaffFields() {
    }
}
