package gg.modl.backend.knowledgebase.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.service.KnowledgebaseArticleService;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.KnowledgebaseArticleResponse;
import gg.modl.proto.modl.v1.KnowledgebaseArticlesResponse;
import gg.modl.proto.modl.v1.KnowledgebaseSearchResponse;
import gg.modl.proto.modl.v1.PublicKnowledgebaseCategoriesResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_KNOWLEDGEBASE)
@RequiredArgsConstructor
public class PublicKnowledgebaseController {
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;
    private final KnowledgebaseProtoMapper mapper;

    @GetMapping("/categories")
    public ResponseEntity<PublicKnowledgebaseCategoriesResponse> getCategories(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        var categories = categoryService.getVisibleCategories(server);
        return ResponseEntity.ok(mapper.toPublicCategoriesResponse(
            categories,
            category -> articleService.getVisibleArticlesByCategory(server, category.getId())));
    }

    @GetMapping("/categories/{categoryId}/articles")
    public ResponseEntity<KnowledgebaseArticlesResponse> getArticles(
        @PathVariable String categoryId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(mapper.toArticlesResponse(
            articleService.getVisibleArticlesByCategory(server, categoryId)));
    }

    @GetMapping("/articles/{idOrSlug}")
    public ResponseEntity<KnowledgebaseArticleResponse> getArticle(
        @PathVariable String idOrSlug,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Optional<KnowledgebaseArticle> article = articleService.getArticleById(server, idOrSlug);
        if (article.isEmpty()) {
            article = articleService.getArticleBySlug(server, idOrSlug);
        }

        return article
            .filter(KnowledgebaseArticle::isVisible)
            .map(found -> ResponseEntity.ok(mapper.toArticleResponse(found)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<KnowledgebaseSearchResponse> searchArticles(
        @RequestParam String q,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(mapper.toSearchResponse(articleService.searchArticles(server, q)));
    }
}
