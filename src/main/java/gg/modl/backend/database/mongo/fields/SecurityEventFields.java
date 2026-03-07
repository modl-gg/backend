package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;
import org.bson.Document;

public final class SecurityEventFields {
    public static final MongoField<Document> ID = MongoFieldNames.raw(Document.class, "_id");
    public static final MongoField<Document> TYPE = MongoFieldNames.raw(Document.class, "type");
    public static final MongoField<Document> SEVERITY = MongoFieldNames.raw(Document.class, "severity");
    public static final MongoField<Document> SOURCE = MongoFieldNames.raw(Document.class, "source");
    public static final MongoField<Document> DESCRIPTION = MongoFieldNames.raw(Document.class, "description");
    public static final MongoField<Document> TIMESTAMP = MongoFieldNames.raw(Document.class, "timestamp");

    private SecurityEventFields() {
    }
}
