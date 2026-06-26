package gg.modl.backend.knowledgebase.service;

import com.github.slugify.Slugify;
import gg.modl.backend.database.mongo.repository.KnowledgebaseArticleMongoRepository;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.CreateArticleRequest;
import gg.modl.proto.modl.v1.UpdateArticleRequest;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgebaseArticleService {
    private final KnowledgebaseArticleMongoRepository articleRepository;
    private final Slugify slugify = Slugify.builder().build();
    private static final int MAX_SEARCH_RESULTS = 20;

    public List<KnowledgebaseArticle> getArticlesByCategory(Server server, String categoryId) {
        return articleRepository.findByCategoryOrdered(server, categoryId);
    }

    public Map<String, List<KnowledgebaseArticle>> getAllArticlesGroupedByCategory(Server server) {
        return articleRepository.findAll(server)
            .stream()
            .collect(Collectors.groupingBy(KnowledgebaseArticle::getCategoryId));
    }

    public List<KnowledgebaseArticle> getVisibleArticlesByCategory(Server server, String categoryId) {
        return articleRepository.findVisibleByCategoryOrdered(server, categoryId);
    }

    public Map<String, List<KnowledgebaseArticle>> getVisibleArticlesGroupedByCategoryIds(Server server, Collection<String> categoryIds) {
        return articleRepository.findVisibleByCategoryIdsOrdered(server, categoryIds)
            .stream()
            .collect(Collectors.groupingBy(KnowledgebaseArticle::getCategoryId));
    }

    public Optional<KnowledgebaseArticle> getArticleById(Server server, String id) {
        return articleRepository.findByArticleId(server, id);
    }

    public Optional<KnowledgebaseArticle> getArticleBySlug(Server server, String slug) {
        return articleRepository.findBySlug(server, slug);
    }

    public KnowledgebaseArticle createArticle(Server server, String categoryId, CreateArticleRequest request) {
        KnowledgebaseArticle article = KnowledgebaseArticle.builder()
            .title(request.getTitle())
            .slug(generateUniqueSlug(server, slugify.slugify(request.getTitle()), null))
            .content(request.getContent())
            .categoryId(categoryId)
            .ordinal(articleRepository.findMaxOrdinalInCategory(server, categoryId) + 1)
            .isVisible(!request.hasIsVisible() || request.getIsVisible())
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        return articleRepository.saveEntity(server, article);
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

    public Optional<KnowledgebaseArticle> updateArticle(Server server, String id, UpdateArticleRequest request) {
        String title = request.hasTitle() ? request.getTitle() : null;
        String uniqueSlug = title != null
                            ? generateUniqueSlug(server, slugify.slugify(title), id)
                            : null;

        return articleRepository.updateArticle(
            server,
            id,
            title,
            uniqueSlug,
            request.hasContent() ? request.getContent() : null,
            request.hasIsVisible() ? request.getIsVisible() : null,
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
}
