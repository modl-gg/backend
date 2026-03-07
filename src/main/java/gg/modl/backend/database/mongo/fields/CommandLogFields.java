package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.player.data.log.CommandLogDocument;

public final class CommandLogFields {
    public static final MongoField<CommandLogDocument> ID = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getId);
    public static final MongoField<CommandLogDocument> UUID = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getUuid);
    public static final MongoField<CommandLogDocument> USERNAME = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getUsername);
    public static final MongoField<CommandLogDocument> COMMAND = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getCommand);
    public static final MongoField<CommandLogDocument> TIMESTAMP = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getTimestamp);
    public static final MongoField<CommandLogDocument> SERVER = MongoFieldNames.field(CommandLogDocument.class, CommandLogDocument::getServer);

    private CommandLogFields() {
    }
}