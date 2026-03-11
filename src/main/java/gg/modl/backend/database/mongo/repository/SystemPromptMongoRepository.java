package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.MongoUpdates;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SystemPromptFields;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SystemPromptMongoRepository extends AbstractGlobalMongoRepository<SystemPrompt> {
    private static final String COLLECTION_NAME = "systemprompts";

    public SystemPromptMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemPrompt.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public Optional<SystemPrompt> findByStrictnessLevel(String strictnessLevel) {
        return findOne(Query.query(MongoQueries.where(SystemPromptFields.STRICTNESS_LEVEL).is(strictnessLevel)));
    }

    public Optional<SystemPrompt> findActiveByStrictnessLevel(String strictnessLevel) {
        return findOne(Query.query(
            MongoQueries.where(SystemPromptFields.STRICTNESS_LEVEL).is(strictnessLevel)
                .and(SystemPromptFields.IS_ACTIVE).is(true)
        ));
    }

    public SystemPrompt upsertPrompt(String strictnessLevel, String prompt, Date now) {
        Query query = Query.query(MongoQueries.where(SystemPromptFields.STRICTNESS_LEVEL).is(strictnessLevel));
        Update update = new Update();
        MongoUpdates.set(update, SystemPromptFields.PROMPT, prompt);
        MongoUpdates.set(update, SystemPromptFields.UPDATED_AT, now);
        MongoUpdates.setOnInsert(update, SystemPromptFields.STRICTNESS_LEVEL, strictnessLevel);
        MongoUpdates.setOnInsert(update, SystemPromptFields.IS_ACTIVE, true);
        MongoUpdates.setOnInsert(update, SystemPromptFields.CREATED_AT, now);
        return findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true));
    }
}

