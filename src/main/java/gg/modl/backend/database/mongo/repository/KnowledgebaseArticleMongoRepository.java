package gg.modl.backend.database.mongo.repository;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.AbstractServerMongoRepository;
import gg.modl.backend.database.mongo.MongoQueries;
import gg.modl.backend.database.mongo.TenantMongoAccess;
import gg.modl.backend.database.mongo.fields.KnowledgebaseArticleFields;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgebaseArticleMongoRepository extends AbstractServerMongoRepository<KnowledgebaseArticle> {
    public KnowledgebaseArticleMongoRepository(TenantMongoAccess tenantMongoAccess) {
        super(KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES, tenantMongoAccess);
    }

    public List<KnowledgebaseArticle> findByCategoryOrdered(Server server, String categoryId) {
        Query query = Query.query(MongoQueries.where(KnowledgebaseArticleFields.CATEGORY_ID).is(categoryId))
            .with(MongoQueries.sort(Sort.Direction.ASC, KnowledgebaseArticleFields.ORDINAL));
        return find(server, query);
    }

    public List<KnowledgebaseArticle> findVisibleByCategoryOrdered(Server server, String categoryId) {
        Query query = Query.query(new Criteria().andOperator(
            MongoQueries.where(KnowledgebaseArticleFields.CATEGORY_ID).is(categoryId),
            MongoQueries.where(KnowledgebaseArticleFields.IS_VISIBLE).is(true)
        )).with(MongoQueries.sort(Sort.Direction.ASC, KnowledgebaseArticleFields.ORDINAL));
        return find(server, query);
    }

    public Optional<KnowledgebaseArticle> findByArticleId(Server server, String id) {
        return findById(server, id);
    }

    public Optional<KnowledgebaseArticle> findBySlug(Server server, String slug) {
        return findOne(server, Query.query(MongoQueries.where(KnowledgebaseArticleFields.SLUG).is(slug)));
    }

    public int findMaxOrdinalInCategory(Server server, String categoryId) {
        Query query = Query.query(MongoQueries.where(KnowledgebaseArticleFields.CATEGORY_ID).is(categoryId))
            .with(MongoQueries.sort(Sort.Direction.DESC, KnowledgebaseArticleFields.ORDINAL))
            .limit(1);
        return findOne(server, query)
            .map(KnowledgebaseArticle::getOrdinal)
            .orElse(-1);
    }

    public boolean existsBySlug(Server server, String slug, String excludeId) {
        Criteria criteria = MongoQueries.where(KnowledgebaseArticleFields.SLUG).is(slug);
        if (excludeId != null) {
            criteria = criteria.and(KnowledgebaseArticleFields.ID).ne(excludeId);
        }
        return exists(server, Query.query(criteria));
    }

    public Optional<KnowledgebaseArticle> updateArticle(
        Server server,
        String id,
        String title,
        String slug,
        String content,
        Boolean isVisible,
        Date updatedAt
    ) {
        Update update = new Update().set(KnowledgebaseArticleFields.UPDATED_AT, updatedAt);
        if (title != null) {
            update.set(KnowledgebaseArticleFields.TITLE, title);
        }
        if (slug != null) {
            update.set(KnowledgebaseArticleFields.SLUG, slug);
        }
        if (content != null) {
            update.set(KnowledgebaseArticleFields.CONTENT, content);
        }
        if (isVisible != null) {
            update.set(KnowledgebaseArticleFields.IS_VISIBLE, isVisible);
        }

        KnowledgebaseArticle updated = findAndModify(
            server,
            Query.query(MongoQueries.where(KnowledgebaseArticleFields.ID).is(id)),
            update,
            FindAndModifyOptions.options().returnNew(true)
        );
        return Optional.ofNullable(updated);
    }

    public boolean deleteByArticleId(Server server, String id) {
        return remove(server, Query.query(MongoQueries.where(KnowledgebaseArticleFields.ID).is(id))).getDeletedCount() > 0;
    }

    public long deleteByCategoryId(Server server, String categoryId) {
        return remove(server, Query.query(MongoQueries.where(KnowledgebaseArticleFields.CATEGORY_ID).is(categoryId))).getDeletedCount();
    }

    public List<KnowledgebaseArticle> searchVisibleArticles(Server server, String searchQuery, int limit) {
        String escapedQuery = Pattern.quote(searchQuery);
        Criteria criteria = new Criteria().andOperator(
            MongoQueries.where(KnowledgebaseArticleFields.IS_VISIBLE).is(true),
            new Criteria().orOperator(
                MongoQueries.where(KnowledgebaseArticleFields.TITLE).regex(Pattern.compile(escapedQuery, Pattern.CASE_INSENSITIVE)),
                MongoQueries.where(KnowledgebaseArticleFields.CONTENT).regex(Pattern.compile(escapedQuery, Pattern.CASE_INSENSITIVE))
            )
        );
        Query query = Query.query(criteria)
            .with(MongoQueries.sort(Sort.Direction.ASC, KnowledgebaseArticleFields.ORDINAL))
            .limit(limit);
        return find(server, query);
    }

    public void reorderArticles(Server server, String categoryId, List<String> ids) {
        for (int index = 0; index < ids.size(); index++) {
            updateFirst(
                server,
                Query.query(new Criteria().andOperator(
                    MongoQueries.where(KnowledgebaseArticleFields.ID).is(ids.get(index)),
                    MongoQueries.where(KnowledgebaseArticleFields.CATEGORY_ID).is(categoryId)
                )),
                new Update().set(KnowledgebaseArticleFields.ORDINAL, index)
            );
        }
    }
}
