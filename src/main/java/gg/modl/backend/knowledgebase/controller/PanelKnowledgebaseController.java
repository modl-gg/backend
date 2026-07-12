package gg.modl.backend.knowledgebase.controller;

import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.service.KnowledgebaseArticleService;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.CreateArticleRequest;
import gg.modl.proto.modl.v1.CreateCategoryRequest;
import gg.modl.proto.modl.v1.KnowledgebaseArticleResponse;
import gg.modl.proto.modl.v1.KnowledgebaseArticlesResponse;
import gg.modl.proto.modl.v1.KnowledgebaseCategoryResponse;
import gg.modl.proto.modl.v1.KnowledgebaseMessageResponse;
import gg.modl.proto.modl.v1.PanelKnowledgebaseCategoriesResponse;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.ReorderRequest;
import gg.modl.proto.modl.v1.UpdateArticleRequest;
import gg.modl.proto.modl.v1.UpdateCategoryRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_KNOWLEDGEBASE)
@RequiresPanelPermission(view = "admin.settings.view.content", modify = "admin.settings.modify.content")
@RequiredArgsConstructor
public class PanelKnowledgebaseController {
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;
    private final KnowledgebaseProtoMapper mapper;
    private final RealtimeEventPublisher publisher;

    @GetMapping("/categories")
    public ResponseEntity<PanelKnowledgebaseCategoriesResponse> getCategories(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        var categories = categoryService.getAllCategories(server);
        Map<String, List<KnowledgebaseArticle>> articlesByCategory =
            articleService.getAllArticlesGroupedByCategory(server);

        return ResponseEntity.ok(mapper.toPanelCategoriesResponse(
            categories,
            category -> articlesByCategory.getOrDefault(category.getId(), Collections.emptyList())));
    }

    @PostMapping("/categories")
    public ResponseEntity<KnowledgebaseCategoryResponse> createCategory(
        @RequestBody CreateCategoryRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        var category = categoryService.createCategory(server, createRequest);
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, category.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toCategoryResponse(category));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<KnowledgebaseCategoryResponse> updateCategory(
        @PathVariable String id,
        @RequestBody UpdateCategoryRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return categoryService.updateCategory(server, id, updateRequest)
            .map(category -> {
                publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, category.getId());
                return ResponseEntity.ok(mapper.toCategoryResponse(category));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<KnowledgebaseMessageResponse> deleteCategory(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = categoryService.deleteCategory(server, id);
        if (deleted) {
            publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, id);
            return ResponseEntity.ok(mapper.message("Category deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/categories/reorder")
    public ResponseEntity<KnowledgebaseMessageResponse> reorderCategories(
        @RequestBody ReorderRequest reorderRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        categoryService.reorderCategories(server, reorderRequest.getIdsList());
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE);
        return ResponseEntity.ok(mapper.message("Categories reordered"));
    }

    @GetMapping("/categories/{categoryId}/articles")
    public ResponseEntity<KnowledgebaseArticlesResponse> getArticles(
        @PathVariable String categoryId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(mapper.toArticlesResponse(
            articleService.getArticlesByCategory(server, categoryId)));
    }

    @GetMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<KnowledgebaseArticleResponse> getArticle(
        @PathVariable String categoryId,
        @PathVariable String articleId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return articleService.getArticleById(server, articleId)
            .map(article -> ResponseEntity.ok(mapper.toArticleResponse(article)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/categories/{categoryId}/articles")
    public ResponseEntity<KnowledgebaseArticleResponse> createArticle(
        @PathVariable String categoryId,
        @RequestBody CreateArticleRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        KnowledgebaseArticle article = articleService.createArticle(server, categoryId, createRequest);
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, article.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toArticleResponse(article));
    }

    @PutMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<KnowledgebaseArticleResponse> updateArticle(
        @PathVariable String categoryId,
        @PathVariable String articleId,
        @RequestBody UpdateArticleRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return articleService.updateArticle(server, articleId, updateRequest)
            .map(article -> {
                publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, article.getId());
                return ResponseEntity.ok(mapper.toArticleResponse(article));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{categoryId}/articles/{articleId}")
    public ResponseEntity<KnowledgebaseMessageResponse> deleteArticle(
        @PathVariable String categoryId,
        @PathVariable String articleId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = articleService.deleteArticle(server, articleId);
        if (deleted) {
            publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE, articleId);
            return ResponseEntity.ok(mapper.message("Article deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/categories/{categoryId}/articles/reorder")
    public ResponseEntity<KnowledgebaseMessageResponse> reorderArticles(
        @PathVariable String categoryId,
        @RequestBody ReorderRequest reorderRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        articleService.reorderArticles(server, categoryId, reorderRequest.getIdsList());
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_KNOWLEDGEBASE);
        return ResponseEntity.ok(mapper.message("Articles reordered"));
    }
}
