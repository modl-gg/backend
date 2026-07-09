package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.dto.response.HomepageCardResponse;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.PublicHomepageCardsResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_HOMEPAGE_CARDS)
@RequiredArgsConstructor
public class PublicHomepageCardController {
    private final HomepageCardService cardService;
    private final HomepageProtoMapper mapper;

    @GetMapping
    public ResponseEntity<PublicHomepageCardsResponse> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<HomepageCardResponse> enrichedCards = cardService.getVisibleCardsEnrichedWithArticles(server).stream()
            .map(enriched -> enriched.category() == null
                ? HomepageCardResponse.from(enriched.card())
                : HomepageCardResponse.from(enriched.card(),
                    HomepageCardResponse.EmbeddedCategory.from(enriched.category(), enriched.articles())))
            .toList();

        return ResponseEntity.ok(mapper.toPublicCardsResponse(enrichedCards));
    }
}
