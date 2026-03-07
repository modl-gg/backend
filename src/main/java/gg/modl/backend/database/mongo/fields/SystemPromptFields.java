package gg.modl.backend.database.mongo.fields;

import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.database.mongo.MongoField;
import gg.modl.backend.database.mongo.MongoFieldNames;

public final class SystemPromptFields {
    public static final MongoField<SystemPrompt> ID = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::getId);
    public static final MongoField<SystemPrompt> STRICTNESS_LEVEL = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::getStrictnessLevel);
    public static final MongoField<SystemPrompt> PROMPT = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::getPrompt);
    public static final MongoField<SystemPrompt> IS_ACTIVE = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::isActive);
    public static final MongoField<SystemPrompt> CREATED_AT = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::getCreatedAt);
    public static final MongoField<SystemPrompt> UPDATED_AT = MongoFieldNames.field(SystemPrompt.class, SystemPrompt::getUpdatedAt);

    private SystemPromptFields() {
    }
}
