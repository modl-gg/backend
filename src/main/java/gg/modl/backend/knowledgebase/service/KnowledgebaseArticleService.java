package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import gg.modl.backend.database.mongo.repository.KnowledgebaseArticleMongoRepository;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.dto.request.CreateArticleRequest;
import gg.modl.backend.knowledgebase.dto.request.UpdateArticleRequest;
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
public class KnowledgebaseArticleService {
    private static final int MAX_SEARCH_RESULTS = 20;

    private final KnowledgebaseArticleMongoRepository articleRepository;
    private final Slugify slugify = Slugify.builder().build();

    public List<KnowledgebaseArticle> getArticlesByCategory(Server server, String categoryId) {
        return articleRepository.findByCategoryOrdered(server, categoryId);
    }

    public List<KnowledgebaseArticle> getVisibleArticlesByCategory(Server server, String categoryId) {
        return articleRepository.findVisibleByCategoryOrdered(server, categoryId);
    }

    public Optional<KnowledgebaseArticle> getArticleById(Server server, String id) {
        return articleRepository.findByArticleId(server, id);
    }

    public Optional<KnowledgebaseArticle> getArticleBySlug(Server server, String slug) {
        return articleRepository.findBySlug(server, slug);
    }

    public KnowledgebaseArticle createArticle(Server server, String categoryId, CreateArticleRequest request) {
        KnowledgebaseArticle article = KnowledgebaseArticle.builder()
                .title(request.title())
                .slug(generateUniqueSlug(server, slugify.slugify(request.title()), null))
                .content(request.content())
                .categoryId(categoryId)
                .ordinal(articleRepository.findMaxOrdinalInCategory(server, categoryId) + 1)
                .isVisible(request.isVisible() != null ? request.isVisible() : true)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        return articleRepository.saveEntity(server, article);
    }

    public Optional<KnowledgebaseArticle> updateArticle(Server server, String id, UpdateArticleRequest request) {
        String uniqueSlug = request.title() != null
                ? generateUniqueSlug(server, slugify.slugify(request.title()), id)
                : null;

        return articleRepository.updateArticle(
                server,
                id,
                request.title(),
                uniqueSlug,
                request.content(),
                request.isVisible(),
                new Date()
        );
    }

    public boolean deleteArticle(Server server, String id) {
        return articleRepository.deleteByArticleId(server, id);
    }

    public List<KnowledgebaseArticle> searchArticles(Server server, String searchQuery) {
        return articleRepository.searchVisibleArticles(server, searchQuery, MAX_SEARCH_RESULTS);
    }

    public void reorderArticles(Server server, String categoryId, List<String> ids) {
        articleRepository.reorderArticles(server, categoryId, ids);
    }

    private String generateUniqueSlug(Server server, String baseSlug, String excludeId) {
        String slug = baseSlug;
        int suffix = 1;

        while (articleRepository.existsBySlug(server, slug, excludeId)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }

        return slug;
    }
}
