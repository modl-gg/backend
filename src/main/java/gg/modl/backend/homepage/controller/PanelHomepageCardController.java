package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.request.CreateCardRequest;
import gg.modl.backend.homepage.dto.request.UpdateCardRequest;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.knowledgebase.dto.request.ReorderRequest;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Date;
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
@RequestMapping(RESTMappingV1.PANEL_HOMEPAGE_CARDS)
@RequiredArgsConstructor
public class PanelHomepageCardController {
    private final HomepageCardService cardService;

    @GetMapping
    public ResponseEntity<List<HomepageCardResponse>> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<HomepageCardService.EnrichedCard> enrichedCards = cardService.getAllCardsEnriched(server);

        List<HomepageCardResponse> response = enrichedCards.stream()
            .map(enriched -> {
                HomepageCard card = enriched.card();
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
                    enriched.category()
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
        @RequestBody @Valid UpdateCardRequest updateRequest,
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
        @RequestBody @Valid ReorderRequest reorderRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        cardService.reorderCards(server, reorderRequest.ids());
        return ResponseEntity.ok(Map.of("message", "Cards reordered"));
    }

    public record HomepageCardResponse(
        String id,
        String title,
        String description,
        String icon,
        String iconColor,
        String actionType,
        String actionUrl,
        String actionButtonText,
        String categoryId,
        String backgroundColor,
        boolean isEnabled,
        int ordinal,
        Date createdAt,
        Date updatedAt,
        HomepageCardService.EmbeddedCategory category
    ) {}
}
