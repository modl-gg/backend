package gg.modl.backend.homepage.service;

import gg.modl.backend.database.mongo.repository.HomepageCardMongoRepository;
import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.request.CreateCardRequest;
import gg.modl.backend.homepage.dto.request.UpdateCardRequest;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.server.data.Server;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomepageCardService {
    private final HomepageCardMongoRepository homepageCardRepository;
    private final KnowledgebaseCategoryService categoryService;

    public List<HomepageCard> getVisibleCards(Server server) {
        return homepageCardRepository.findVisibleOrdered(server);
    }

    public Optional<HomepageCard> getCardById(Server server, String id) {
        return homepageCardRepository.findByCardId(server, id);
    }

    public HomepageCard createCard(Server server, CreateCardRequest request) {
        HomepageCard card = HomepageCard.builder()
            .title(request.title())
            .description(request.description())
            .icon(request.icon())
            .iconColor(request.iconColor())
            .actionType(request.actionType())
            .actionUrl(request.actionUrl())
            .actionButtonText(request.actionButtonText())
            .categoryId(request.categoryId())
            .backgroundColor(request.backgroundColor())
            .ordinal(homepageCardRepository.findMaxOrdinal(server) + 1)
            .isEnabled(request.isEnabled() != null ? request.isEnabled() : true)
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        return homepageCardRepository.saveEntity(server, card);
    }

    public Optional<HomepageCard> updateCard(Server server, String id, UpdateCardRequest request) {
        return homepageCardRepository.updateCard(
            server,
            id,
            request.title(),
            request.description(),
            request.icon(),
            request.iconColor(),
            request.actionType(),
            request.actionUrl(),
            request.actionButtonText(),
            request.categoryId(),
            request.backgroundColor(),
            request.isEnabled(),
            new Date()
        );
    }

    public boolean deleteCard(Server server, String id) {
        return homepageCardRepository.deleteByCardId(server, id);
    }

    public void reorderCards(Server server, List<String> ids) {
        homepageCardRepository.reorderCards(server, ids);
    }

    public List<EnrichedCard> getAllCardsEnriched(Server server) {
        List<HomepageCard> cards = getAllCards(server);

        List<String> categoryIds = cards.stream()
            .map(HomepageCard::getCategoryId)
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .toList();

        Map<String, KnowledgebaseCategory> categoriesById = categoryIds.stream()
            .map(id -> categoryService.getCategoryById(server, id).orElse(null))
            .filter(cat -> cat != null)
            .collect(Collectors.toMap(KnowledgebaseCategory::getId, Function.identity()));

        return cards.stream()
            .map(card -> {
                EmbeddedCategory embedded = null;
                if (card.getCategoryId() != null && !card.getCategoryId().isEmpty()) {
                    KnowledgebaseCategory cat = categoriesById.get(card.getCategoryId());
                    if (cat != null) {
                        embedded = new EmbeddedCategory(cat.getId(), cat.getName(), cat.getSlug());
                    }
                }
                return new EnrichedCard(card, embedded);
            })
            .toList();
    }

    public List<HomepageCard> getAllCards(Server server) {
        return homepageCardRepository.findAllOrdered(server);
    }

    public record EmbeddedCategory(String id, String name, String slug) {}

    public record EnrichedCard(
        HomepageCard card,
        EmbeddedCategory category
    ) {}
}
