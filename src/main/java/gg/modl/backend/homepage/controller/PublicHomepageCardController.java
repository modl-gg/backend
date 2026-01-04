package gg.modl.backend.homepage.controller;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_HOMEPAGE_CARDS)
@RequiredArgsConstructor
public class PublicHomepageCardController {
    private final HomepageCardService cardService;

    @GetMapping
    public ResponseEntity<List<HomepageCard>> getCards(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        List<HomepageCard> cards = cardService.getVisibleCards(server);
        return ResponseEntity.ok(cards);
    }
}
