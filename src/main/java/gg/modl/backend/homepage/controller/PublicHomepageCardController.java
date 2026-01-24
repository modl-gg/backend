package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.response.HomepageCardResponse;
import gg.modl.backend.homepage.service.HomepageCardService;
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
@RequestMapping(RESTMappingV1.PUBLIC_HOMEPAGE_CARDS)
@RequiredArgsConstructor
public class PublicHomepageCardController {
    private final HomepageCardService cardService;
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;

    @GetMapping
    public ResponseEntity<List<HomepageCardResponse>> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<HomepageCard> cards = cardService.getVisibleCards(server);
        
        List<HomepageCardResponse> enrichedCards = cards.stream()
                .map(card -> enrichCard(server, card))
                .toList();
        
        return ResponseEntity.ok(enrichedCards);
    }
    
    private HomepageCardResponse enrichCard(Server server, HomepageCard card) {
        if (!"category_dropdown".equals(card.getActionType()) || card.getCategoryId() == null) {
            return HomepageCardResponse.from(card);
        }
        
        Optional<KnowledgebaseCategory> categoryOpt = categoryService.getCategoryById(server, card.getCategoryId());
        if (categoryOpt.isEmpty()) {
            return HomepageCardResponse.from(card);
        }
        
        KnowledgebaseCategory category = categoryOpt.get();
        List<KnowledgebaseArticle> articles = articleService.getVisibleArticlesByCategory(server, category.getId());
        
        HomepageCardResponse.EmbeddedCategory embeddedCategory = 
                HomepageCardResponse.EmbeddedCategory.from(category, articles);
        
        return HomepageCardResponse.from(card, embeddedCategory);
    }
}
