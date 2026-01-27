package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.request.CreateCardRequest;
import gg.modl.backend.homepage.dto.request.UpdateCardRequest;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.dto.request.ReorderRequest;
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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(RESTMappingV1.PANEL_HOMEPAGE_CARDS)
@RequiredArgsConstructor
public class PanelHomepageCardController {
    private final HomepageCardService cardService;
    private final KnowledgebaseCategoryService categoryService;

    public record EmbeddedCategory(String id, String name, String slug) {}

    public record HomepageCardResponse(
            String id,
            String title,
            String description,
            String icon,
            String icon_color,
            String action_type,
            String action_url,
            String action_button_text,
            String category_id,
            String background_color,
            boolean is_enabled,
            int ordinal,
            Date created_at,
            Date updated_at,
            EmbeddedCategory category
    ) {}

    @GetMapping
    public ResponseEntity<List<HomepageCardResponse>> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<HomepageCard> cards = cardService.getAllCards(server);

        List<HomepageCardResponse> response = cards.stream()
                .map(card -> {
                    EmbeddedCategory embeddedCategory = null;
                    if (card.getCategoryId() != null && !card.getCategoryId().isEmpty()) {
                        Optional<KnowledgebaseCategory> categoryOpt = categoryService.getCategoryById(server, card.getCategoryId());
                        if (categoryOpt.isPresent()) {
                            KnowledgebaseCategory cat = categoryOpt.get();
                            embeddedCategory = new EmbeddedCategory(cat.getId(), cat.getName(), cat.getSlug());
                        }
                    }
                    return new HomepageCardResponse(
                            card.getId(),
                            card.getTitle(),
                            card.getDescription(),
                            card.getIcon(),
                            card.getIconColor(),
                            card.getActionType(),
                            card.getActionUrl(),
                            card.getActionButtonText(),
                            card.getCategoryId(),
                            card.getBackgroundColor(),
                            card.isEnabled(),
                            card.getOrdinal(),
                            card.getCreatedAt(),
                            card.getUpdatedAt(),
                            embeddedCategory
                    );
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<HomepageCard> createCard(
            @RequestBody @Valid CreateCardRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        HomepageCard card = cardService.createCard(server, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(card);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HomepageCard> updateCard(
            @PathVariable String id,
            @RequestBody UpdateCardRequest updateRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return cardService.updateCard(server, id, updateRequest)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCard(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = cardService.deleteCard(server, id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Card deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<?> reorderCards(
            @RequestBody ReorderRequest reorderRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        cardService.reorderCards(server, reorderRequest.ids());
        return ResponseEntity.ok(Map.of("message", "Cards reordered"));
    }
}
