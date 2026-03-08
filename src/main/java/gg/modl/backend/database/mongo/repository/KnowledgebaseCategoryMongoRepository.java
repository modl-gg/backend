package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.KnowledgebaseCategoryFields;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.server.data.Server;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgebaseCategoryMongoRepository extends AbstractServerMongoRepository<KnowledgebaseCategory> {
    public KnowledgebaseCategoryMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES, tenantMongoAccess);
    }

    public List<KnowledgebaseCategory> findAllOrdered(Server server) {
        return find(server, new Query().with(MongoQueries.sort(Sort.Direction.ASC, KnowledgebaseCategoryFields.ORDINAL)));
    }

    public List<KnowledgebaseCategory> findVisibleOrdered(Server server) {
        Query query = Query.query(MongoQueries.where(KnowledgebaseCategoryFields.IS_VISIBLE).is(true))
                .with(MongoQueries.sort(Sort.Direction.ASC, KnowledgebaseCategoryFields.ORDINAL));
        return find(server, query);
    }

    public Optional<KnowledgebaseCategory> findByCategoryId(Server server, String id) {
        return findById(server, id);
    }

    public int findMaxOrdinal(Server server) {
        Query query = new Query()
                .with(MongoQueries.sort(Sort.Direction.DESC, KnowledgebaseCategoryFields.ORDINAL))
                .limit(1);
        return findOne(server, query)
                .map(KnowledgebaseCategory::getOrdinal)
                .orElse(-1);
    }

    public Optional<KnowledgebaseCategory> updateCategory(
            Server server,
            String id,
            String name,
            String slug,
            String description,
            Boolean isVisible,
            Date updatedAt
    ) {
        Update update = new Update().set(KnowledgebaseCategoryFields.UPDATED_AT, updatedAt);
        if (name != null) {
            update.set(KnowledgebaseCategoryFields.NAME, name);
        }
        if (slug != null) {
            update.set(KnowledgebaseCategoryFields.SLUG, slug);
        }
        if (description != null) {
            update.set(KnowledgebaseCategoryFields.DESCRIPTION, description);
        }
        if (isVisible != null) {
            update.set(KnowledgebaseCategoryFields.IS_VISIBLE, isVisible);
        }

        KnowledgebaseCategory updated = findAndModify(
                server,
                Query.query(MongoQueries.where(KnowledgebaseCategoryFields.ID).is(id)),
                update,
                FindAndModifyOptions.options().returnNew(true)
        );
        return Optional.ofNullable(updated);
    }

    public boolean deleteByCategoryId(Server server, String id) {
        return remove(server, Query.query(MongoQueries.where(KnowledgebaseCategoryFields.ID).is(id))).getDeletedCount() > 0;
    }

    public void reorderCategories(Server server, List<String> ids) {
        for (int index = 0; index < ids.size(); index++) {
            updateFirst(
                    server,
                    Query.query(MongoQueries.where(KnowledgebaseCategoryFields.ID).is(ids.get(index))),
                    new Update().set(KnowledgebaseCategoryFields.ORDINAL, index)
            );
        }
    }
}
