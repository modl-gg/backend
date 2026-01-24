package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import com.mongodb.client.result.DeleteResult;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.dto.request.CreateArticleRequest;
import gg.modl.backend.knowledgebase.dto.request.UpdateArticleRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseArticleService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final Slugify slugify = Slugify.builder().build();

    public List<KnowledgebaseArticle> getArticlesByCategory(Server server, String categoryId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("categoryId").is(categoryId))
                .with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
    }

    public List<KnowledgebaseArticle> getVisibleArticlesByCategory(Server server, String categoryId) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("categoryId").is(categoryId).and("isVisible").is(true))
                .with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
    }

    public Optional<KnowledgebaseArticle> getArticleById(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        return Optional.ofNullable(template.findOne(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES));
    }

    public Optional<KnowledgebaseArticle> getArticleBySlug(Server server, String slug) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("slug").is(slug));
        return Optional.ofNullable(template.findOne(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES));
    }

    public KnowledgebaseArticle createArticle(Server server, String categoryId, CreateArticleRequest request) {
        MongoTemplate template = getTemplate(server);

        int maxOrdinal = getMaxOrdinalInCategory(template, categoryId);

        KnowledgebaseArticle article = KnowledgebaseArticle.builder()
                .title(request.title())
                .slug(slugify.slugify(request.title()))
                .content(request.content())
                .categoryId(categoryId)
                .ordinal(maxOrdinal + 1)
                .isVisible(request.isVisible() != null ? request.isVisible() : true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(article, CollectionName.KNOWLEDGEBASE_ARTICLES);
        return article;
    }

    public Optional<KnowledgebaseArticle> updateArticle(Server server, String id, UpdateArticleRequest request) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));

        KnowledgebaseArticle article = template.findOne(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
        if (article == null) {
            return Optional.empty();
        }

        Update update = new Update().set("updatedAt", new Date());

        if (request.title() != null) {
            update.set("title", request.title());
            update.set("slug", slugify.slugify(request.title()));
        }
        if (request.content() != null) {
            update.set("content", request.content());
        }
        if (request.isVisible() != null) {
            update.set("isVisible", request.isVisible());
        }

        template.updateFirst(query, update, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
        return getArticleById(server, id);
    }

    public boolean deleteArticle(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        DeleteResult result = template.remove(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
        return result.getDeletedCount() > 0;
    }

    public List<KnowledgebaseArticle> searchArticles(Server server, String searchQuery) {
        MongoTemplate template = getTemplate(server);

        String escapedQuery = Pattern.quote(searchQuery);
        Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("title").regex(Pattern.compile(escapedQuery, Pattern.CASE_INSENSITIVE)),
                Criteria.where("content").regex(Pattern.compile(escapedQuery, Pattern.CASE_INSENSITIVE))
        );

        Query query = Query.query(searchCriteria.and("isVisible").is(true))
                .with(Sort.by(Sort.Direction.ASC, "ordinal"))
                .limit(20);

        return template.find(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
    }

    public void reorderArticles(Server server, String categoryId, List<String> ids) {
        MongoTemplate template = getTemplate(server);

        for (int i = 0; i < ids.size(); i++) {
            Query query = Query.query(Criteria.where("_id").is(ids.get(i)).and("categoryId").is(categoryId));
            Update update = new Update().set("ordinal", i);
            template.updateFirst(query, update, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
        }
    }

    private int getMaxOrdinalInCategory(MongoTemplate template, String categoryId) {
        Query query = Query.query(Criteria.where("categoryId").is(categoryId))
                .with(Sort.by(Sort.Direction.DESC, "ordinal"))
                .limit(1);
        KnowledgebaseArticle highest = template.findOne(query, KnowledgebaseArticle.class, CollectionName.KNOWLEDGEBASE_ARTICLES);
        return highest != null ? highest.getOrdinal() : -1;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
