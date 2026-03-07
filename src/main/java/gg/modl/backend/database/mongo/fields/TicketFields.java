package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;

public final class TicketFields {
    public static final MongoField<Ticket> ID = MongoFieldNames.field(Ticket.class, Ticket::getId);
    public static final MongoField<Ticket> TYPE = MongoFieldNames.field(Ticket.class, Ticket::getType);
    public static final MongoField<Ticket> CATEGORY = MongoFieldNames.field(Ticket.class, Ticket::getCategory);
    public static final MongoField<Ticket> SUBJECT = MongoFieldNames.field(Ticket.class, Ticket::getSubject);
    public static final MongoField<Ticket> STATUS = MongoFieldNames.field(Ticket.class, Ticket::getStatus);
    public static final MongoField<Ticket> PRIORITY = MongoFieldNames.field(Ticket.class, Ticket::getPriority);
    public static final MongoField<Ticket> CREATOR_UUID = MongoFieldNames.field(Ticket.class, Ticket::getCreatorUuid);
    public static final MongoField<Ticket> CREATOR_NAME = MongoFieldNames.field(Ticket.class, Ticket::getCreatorName);
    public static final MongoField<Ticket> REPORTED_PLAYER_UUID = MongoFieldNames.field(Ticket.class, Ticket::getReportedPlayerUuid);
    public static final MongoField<Ticket> TAGS = MongoFieldNames.field(Ticket.class, Ticket::getTags);
    public static final MongoField<Ticket> REPLIES = MongoFieldNames.field(Ticket.class, Ticket::getReplies);
    public static final MongoField<Ticket> REPLY_NAME = MongoFieldNames.field(Ticket.class, Ticket::getReplies, TicketReply.class, TicketReply::getName);
    public static final MongoField<Ticket> REPLY_CONTENT = MongoFieldNames.field(Ticket.class, Ticket::getReplies, TicketReply.class, TicketReply::getContent);
    public static final MongoField<Ticket> REPLY_CREATED = MongoFieldNames.field(Ticket.class, Ticket::getReplies, TicketReply.class, TicketReply::getCreated);
    public static final MongoField<Ticket> LOCKED = MongoFieldNames.field(Ticket.class, Ticket::isLocked);
    public static final MongoField<Ticket> ASSIGNED_TO = MongoFieldNames.field(Ticket.class, Ticket::getAssignedTo);
    public static final MongoField<Ticket> CREATED = MongoFieldNames.field(Ticket.class, Ticket::getCreated);
    public static final MongoField<Ticket> UPDATED_AT = MongoFieldNames.field(Ticket.class, Ticket::getUpdatedAt);
    public static final MongoField<Ticket> AI_ANALYSIS = MongoFieldNames.field(Ticket.class, Ticket::getAiAnalysis);
    public static final MongoField<Ticket> HIDDEN = MongoFieldNames.field(Ticket.class, Ticket::isHidden);

    private TicketFields() {
    }
}
