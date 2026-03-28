package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.admin.data.SystemPrompt;
import gg.modl.backend.database.mongo.AbstractGlobalMongoRepository;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.SystemPromptFields;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SystemPromptMongoRepository extends AbstractGlobalMongoRepository<SystemPrompt> {
    private static final String COLLECTION_NAME = "systemprompts";

    public SystemPromptMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(SystemPrompt.class, COLLECTION_NAME, tenantMongoAccess);
    }

    public Optional<SystemPrompt> findActive() {
        return findOne(Query.query(Criteria.where(SystemPromptFields.IS_ACTIVE).is(true)));
    }

    public SystemPrompt upsertPrompt(String prompt, Date now) {
        Query query = Query.query(Criteria.where(SystemPromptFields.IS_ACTIVE).is(true));
        Update update = new Update();
        update.set(SystemPromptFields.PROMPT, prompt);
        update.set(SystemPromptFields.UPDATED_AT, now);
        update.setOnInsert(SystemPromptFields.IS_ACTIVE, true);
        update.setOnInsert(SystemPromptFields.CREATED_AT, now);
        return findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true));
    }
}
