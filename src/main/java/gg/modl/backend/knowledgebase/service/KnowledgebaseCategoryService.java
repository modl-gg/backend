package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import gg.modl.backend.database.mongo.repository.KnowledgebaseArticleMongoRepository;
import gg.modl.backend.database.mongo.repository.KnowledgebaseCategoryMongoRepository;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.CreateCategoryRequest;
import gg.modl.proto.modl.v1.UpdateCategoryRequest;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseCategoryService {
    private final KnowledgebaseCategoryMongoRepository categoryRepository;
    private final KnowledgebaseArticleMongoRepository articleRepository;
    private final Slugify slugify = Slugify.builder().build();

    public List<KnowledgebaseCategory> getAllCategories(Server server) {
        return categoryRepository.findAllOrdered(server);
    }

    public List<KnowledgebaseCategory> getVisibleCategories(Server server) {
        return categoryRepository.findVisibleOrdered(server);
    }

    public Optional<KnowledgebaseCategory> getCategoryById(Server server, String id) {
        return categoryRepository.findByCategoryId(server, id);
    }

    public List<KnowledgebaseCategory> getCategoriesByIds(Server server, Collection<String> ids) {
        return categoryRepository.findByIds(server, ids);
    }

    public KnowledgebaseCategory createCategory(Server server, CreateCategoryRequest request) {
        KnowledgebaseCategory category = KnowledgebaseCategory.builder()
            .name(request.getName())
            .slug(slugify.slugify(request.getName()))
            .description(request.hasDescription() ? request.getDescription() : null)
            .ordinal(categoryRepository.findMaxOrdinal(server) + 1)
            .isVisible(true)
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        return categoryRepository.saveEntity(server, category);
    }

    public Optional<KnowledgebaseCategory> updateCategory(Server server, String id, UpdateCategoryRequest request) {
        String name = request.hasName() ? request.getName() : null;
        return categoryRepository.updateCategory(
            server,
            id,
            name,
            name != null ? slugify.slugify(name) : null,
            request.hasDescription() ? request.getDescription() : null,
            request.hasIsVisible() ? request.getIsVisible() : null,
            new Date()
        );
    }

    public boolean deleteCategory(Server server, String id) {
        articleRepository.deleteByCategoryId(server, id);
        return categoryRepository.deleteByCategoryId(server, id);
    }

    public void reorderCategories(Server server, List<String> ids) {
        categoryRepository.reorderCategories(server, ids);
    }
}
