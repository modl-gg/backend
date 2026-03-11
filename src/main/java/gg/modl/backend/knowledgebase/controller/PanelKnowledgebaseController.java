package gg.modl.backend.knowledgebase.controller;

import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.dto.request.*;
import gg.modl.backend.knowledgebase.service.KnowledgebaseArticleService;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_KNOWLEDGEBASE)
@RequiredArgsConstructor
public class PanelKnowledgebaseController {
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;

    public record ArticleResponse(
            String id,
            String title,
            String slug,
            String content,
            String categoryId,
            int ordinal,
            boolean isVisible,
            Date createdAt,
            Date updatedAt
    ) {}

    public record CategoryWithArticlesResponse(
            String id,
            String name,
            String slug,
            String description,
            int ordinal,
            boolean isVisible,
            List<ArticleResponse> articles
    ) {}

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryWithArticlesResponse>> getCategories(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<KnowledgebaseCategory> categories = categoryService.getAllCategories(server);
        Map<String, List<KnowledgebaseArticle>> articlesByCategory = articleService.getAllArticlesGroupedByCategory(server);

        List<CategoryWithArticlesResponse> response = categories.stream()
                .map(category -> new CategoryWithArticlesResponse(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getDescription(),
                        category.getOrdinal(),
                        category.isVisible(),
                        articlesByCategory.getOrDefault(category.getId(), Collections.emptyList())
                                .stream()
                                .map(this::toArticleResponse)
                                .toList()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/categories")
    public ResponseEntity<KnowledgebaseCategory> createCategory(
            @RequestBody @Valid CreateCategoryRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        KnowledgebaseCategory category = categoryService.createCategory(server, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<KnowledgebaseCategory> updateCategory(
            @PathVariable String id,
            @RequestBody @Valid UpdateCategoryRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return categoryService.updateCategory(server, id, updateRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = categoryService.deleteCategory(server, id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Category deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/categories/reorder")
    public ResponseEntity<?> reorderCategories(
            @RequestBody @Valid ReorderRequest reorderRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        categoryService.reorderCategories(server, reorderRequest.ids());
        return ResponseEntity.ok(Map.of("message", "Categories reordered"));
    }

    @GetMapping("/categories/{categoryId}/articles")
    public ResponseEntity<List<ArticleResponse>> getArticles(
            @PathVariable String categoryId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<ArticleResponse> articles = articleService.getArticlesByCategory(server, categoryId)
                .stream()
                .map(this::toArticleResponse)
                .toList();
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<ArticleResponse> getArticle(
            @PathVariable String categoryId,
            @PathVariable String articleId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return articleService.getArticleById(server, articleId)
                .map(this::toArticleResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/categories/{categoryId}/articles")
    public ResponseEntity<ArticleResponse> createArticle(
            @PathVariable String categoryId,
            @RequestBody @Valid CreateArticleRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        KnowledgebaseArticle article = articleService.createArticle(server, categoryId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toArticleResponse(article));
    }

    @PutMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<ArticleResponse> updateArticle(
            @PathVariable String categoryId,
            @PathVariable String articleId,
            @RequestBody @Valid UpdateArticleRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return articleService.updateArticle(server, articleId, updateRequest)
                .map(this::toArticleResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<?> deleteArticle(
            @PathVariable String categoryId,
            @PathVariable String articleId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = articleService.deleteArticle(server, articleId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Article deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/categories/{categoryId}/articles/reorder")
    public ResponseEntity<?> reorderArticles(
            @PathVariable String categoryId,
            @RequestBody @Valid ReorderRequest reorderRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        articleService.reorderArticles(server, categoryId, reorderRequest.ids());
        return ResponseEntity.ok(Map.of("message", "Articles reordered"));
    }

    private ArticleResponse toArticleResponse(KnowledgebaseArticle article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getContent(),
                article.getCategoryId(),
                article.getOrdinal(),
                article.isVisible(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
