package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import gg.modl.backend.database.mongo.repository.KnowledgebaseArticleMongoRepository;
import gg.modl.backend.database.mongo.repository.KnowledgebaseCategoryMongoRepository;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.dto.request.CreateCategoryRequest;
import gg.modl.backend.knowledgebase.dto.request.UpdateCategoryRequest;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

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

    public KnowledgebaseCategory createCategory(Server server, CreateCategoryRequest request) {
        KnowledgebaseCategory category = KnowledgebaseCategory.builder()
                .name(request.name())
                .slug(slugify.slugify(request.name()))
                .description(request.description())
                .ordinal(categoryRepository.findMaxOrdinal(server) + 1)
                .isVisible(true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        return categoryRepository.saveEntity(server, category);
    }

    public Optional<KnowledgebaseCategory> updateCategory(Server server, String id, UpdateCategoryRequest request) {
        return categoryRepository.updateCategory(
                server,
                id,
                request.name(),
                request.name() != null ? slugify.slugify(request.name()) : null,
                request.description(),
                request.isVisible(),
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
