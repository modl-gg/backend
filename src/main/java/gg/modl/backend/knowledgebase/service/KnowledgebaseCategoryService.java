package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.dto.request.CreateCategoryRequest;
import gg.modl.backend.knowledgebase.dto.request.UpdateCategoryRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseCategoryService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final Slugify slugify = Slugify.builder().build();

    public List<KnowledgebaseCategory> getAllCategories(Server server) {
        MongoTemplate template = getTemplate(server);
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
    }

    public List<KnowledgebaseCategory> getVisibleCategories(Server server) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("isVisible").is(true))
                .with(Sort.by(Sort.Direction.ASC, "ordinal"));
        return template.find(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
    }

    public Optional<KnowledgebaseCategory> getCategoryById(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        return Optional.ofNullable(template.findOne(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES));
    }

    public KnowledgebaseCategory createCategory(Server server, CreateCategoryRequest request) {
        MongoTemplate template = getTemplate(server);

        int maxOrdinal = getMaxOrdinal(template);

        KnowledgebaseCategory category = KnowledgebaseCategory.builder()
                .name(request.name())
                .slug(slugify.slugify(request.name()))
                .description(request.description())
                .ordinal(maxOrdinal + 1)
                .isVisible(true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        template.save(category, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        return category;
    }

    public Optional<KnowledgebaseCategory> updateCategory(Server server, String id, UpdateCategoryRequest request) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));

        KnowledgebaseCategory category = template.findOne(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        if (category == null) {
            return Optional.empty();
        }

        Update update = new Update().set("updatedAt", new Date());

        if (request.name() != null) {
            update.set("name", request.name());
            update.set("slug", slugify.slugify(request.name()));
        }
        if (request.description() != null) {
            update.set("description", request.description());
        }
        if (request.isVisible() != null) {
            update.set("isVisible", request.isVisible());
        }

        template.updateFirst(query, update, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        return getCategoryById(server, id);
    }

    public boolean deleteCategory(Server server, String id) {
        MongoTemplate template = getTemplate(server);
        Query query = Query.query(Criteria.where("_id").is(id));
        var result = template.remove(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        return result.getDeletedCount() > 0;
    }

    public void reorderCategories(Server server, List<String> ids) {
        MongoTemplate template = getTemplate(server);

        for (int i = 0; i < ids.size(); i++) {
            Query query = Query.query(Criteria.where("_id").is(ids.get(i)));
            Update update = new Update().set("ordinal", i);
            template.updateFirst(query, update, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        }
    }

    private int getMaxOrdinal(MongoTemplate template) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "ordinal")).limit(1);
        KnowledgebaseCategory highest = template.findOne(query, KnowledgebaseCategory.class, CollectionName.KNOWLEDGEBASE_CATEGORIES);
        return highest != null ? highest.getOrdinal() : -1;
    }

    private MongoTemplate getTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }
}
