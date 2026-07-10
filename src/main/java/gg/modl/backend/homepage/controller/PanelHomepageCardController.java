package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.CreateCardRequest;
import gg.modl.proto.modl.v1.HomepageCardMutationResponse;
import gg.modl.proto.modl.v1.PanelHomepageCardResponse;
import gg.modl.proto.modl.v1.PanelHomepageCardsResponse;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.ReorderRequest;
import gg.modl.proto.modl.v1.UpdateCardRequest;
import jakarta.servlet.http.HttpServletRequest;
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
    private final HomepageProtoMapper mapper;
    private final RealtimeEventPublisher publisher;

    @GetMapping
    public ResponseEntity<PanelHomepageCardsResponse> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(mapper.toPanelCardsResponse(cardService.getAllCardsEnriched(server)));
    }

    @PostMapping
    public ResponseEntity<PanelHomepageCardResponse> createCard(
        @RequestBody CreateCardRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        HomepageCard card = cardService.createCard(server, createRequest);
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_HOMEPAGE, card.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toPanelCardResponse(card));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PanelHomepageCardResponse> updateCard(
        @PathVariable String id,
        @RequestBody UpdateCardRequest updateRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return cardService.updateCard(server, id, updateRequest)
            .map(card -> {
                publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_HOMEPAGE, card.getId());
                return ResponseEntity.ok(mapper.toPanelCardResponse(card));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HomepageCardMutationResponse> deleteCard(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        boolean deleted = cardService.deleteCard(server, id);
        if (deleted) {
            publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_HOMEPAGE, id);
            return ResponseEntity.ok(mapper.message("Card deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<HomepageCardMutationResponse> reorderCards(
        @RequestBody ReorderRequest reorderRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        cardService.reorderCards(server, reorderRequest.getIdsList());
        publisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_HOMEPAGE);
        return ResponseEntity.ok(mapper.message("Cards reordered"));
    }
}
