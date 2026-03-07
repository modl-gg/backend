package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import gg.modl.backend.player.data.log.ChatLogDocument;

public final class ChatLogFields {
    public static final MongoField<ChatLogDocument> ID = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getId);
    public static final MongoField<ChatLogDocument> UUID = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getUuid);
    public static final MongoField<ChatLogDocument> USERNAME = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getUsername);
    public static final MongoField<ChatLogDocument> MESSAGE = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getMessage);
    public static final MongoField<ChatLogDocument> TIMESTAMP = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getTimestamp);
    public static final MongoField<ChatLogDocument> SERVER = MongoFieldNames.field(ChatLogDocument.class, ChatLogDocument::getServer);

    private ChatLogFields() {
    }
}