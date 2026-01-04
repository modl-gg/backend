package gg.modl.backend.knowledgebase.controller;

import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.service.KnowledgebaseArticleService;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_KNOWLEDGEBASE)
@RequiredArgsConstructor
public class PublicKnowledgebaseController {
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;

    @GetMapping("/categories")
    public ResponseEntity<List<KnowledgebaseCategory>> getCategories(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<KnowledgebaseCategory> categories = categoryService.getVisibleCategories(server);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/{categoryId}/articles")
    public ResponseEntity<List<KnowledgebaseArticle>> getArticles(
            @PathVariable String categoryId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<KnowledgebaseArticle> articles = articleService.getVisibleArticlesByCategory(server, categoryId);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/articles/{idOrSlug}")
    public ResponseEntity<KnowledgebaseArticle> getArticle(
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
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<KnowledgebaseArticle>> searchArticles(
            @RequestParam String q,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<KnowledgebaseArticle> articles = articleService.searchArticles(server, q);
        return ResponseEntity.ok(articles);
    }
}
