package gg.modl.backend.homepage.service;

import gg.modl.backend.database.mongo.repository.HomepageCardMongoRepository;
import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.backend.knowledgebase.service.KnowledgebaseArticleService;
import gg.modl.backend.knowledgebase.service.KnowledgebaseCategoryService;
import gg.modl.backend.infrastructure.validation.SafeUrls;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.CreateCardRequest;
import gg.modl.proto.modl.v1.UpdateCardRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomepageCardService {
    private static final String CATEGORY_DROPDOWN_ACTION_TYPE = "category_dropdown";

    private final HomepageCardMongoRepository homepageCardRepository;
    private final KnowledgebaseCategoryService categoryService;
    private final KnowledgebaseArticleService articleService;

    public List<HomepageCard> getVisibleCards(Server server) {
        return homepageCardRepository.findVisibleOrdered(server);
    }

    public List<EnrichedCardWithArticles> getVisibleCardsEnrichedWithArticles(Server server) {
        List<HomepageCard> cards = getVisibleCards(server);

        List<String> categoryIds = distinctCategoryIds(cards,
            card -> CATEGORY_DROPDOWN_ACTION_TYPE.equals(card.getActionType()));

        Map<String, KnowledgebaseCategory> categoriesById = categoriesByIdMap(server, categoryIds);

        Map<String, List<KnowledgebaseArticle>> articlesByCategoryId =
            articleService.getVisibleArticlesGroupedByCategoryIds(server, categoryIds);

        return cards.stream()
            .map(card -> {
                KnowledgebaseCategory category = null;
                if (CATEGORY_DROPDOWN_ACTION_TYPE.equals(card.getActionType())
                    && card.getCategoryId() != null && !card.getCategoryId().isEmpty()) {
                    category = categoriesById.get(card.getCategoryId());
                }
                List<KnowledgebaseArticle> articles = category != null
                    ? articlesByCategoryId.getOrDefault(category.getId(), List.of())
                    : List.of();
                return new EnrichedCardWithArticles(card, category, articles);
            })
            .toList();
    }

    public Optional<HomepageCard> getCardById(Server server, String id) {
        return homepageCardRepository.findByCardId(server, id);
    }

    public HomepageCard createCard(Server server, CreateCardRequest request) {
        String actionUrl = request.hasActionUrl() ? request.getActionUrl() : null;
        SafeUrls.requireSafe(actionUrl, "Invalid card URL");
        HomepageCard card = HomepageCard.builder()
            .title(request.getTitle())
            .description(request.hasDescription() ? request.getDescription() : null)
            .icon(request.hasIcon() ? request.getIcon() : null)
            .iconColor(request.hasIconColor() ? request.getIconColor() : null)
            .actionType(request.hasActionType() ? request.getActionType() : null)
            .actionUrl(actionUrl)
            .actionButtonText(request.hasActionButtonText() ? request.getActionButtonText() : null)
            .categoryId(request.hasCategoryId() ? request.getCategoryId() : null)
            .backgroundColor(request.hasBackgroundColor() ? request.getBackgroundColor() : null)
            .ordinal(homepageCardRepository.findMaxOrdinal(server) + 1)
            .isEnabled(!request.hasIsEnabled() || request.getIsEnabled())
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        return homepageCardRepository.saveEntity(server, card);
    }

    public Optional<HomepageCard> updateCard(Server server, String id, UpdateCardRequest request) {
        String actionUrl = request.hasActionUrl() ? request.getActionUrl() : null;
        SafeUrls.requireSafe(actionUrl, "Invalid card URL");
        return homepageCardRepository.updateCard(
            server,
            id,
            request.hasTitle() ? request.getTitle() : null,
            request.hasDescription() ? request.getDescription() : null,
            request.hasIcon() ? request.getIcon() : null,
            request.hasIconColor() ? request.getIconColor() : null,
            request.hasActionType() ? request.getActionType() : null,
            actionUrl,
            request.hasActionButtonText() ? request.getActionButtonText() : null,
            request.hasCategoryId() ? request.getCategoryId() : null,
            request.hasBackgroundColor() ? request.getBackgroundColor() : null,
            request.hasIsEnabled() ? request.getIsEnabled() : null,
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

        List<String> categoryIds = distinctCategoryIds(cards, card -> true);

        Map<String, KnowledgebaseCategory> categoriesById = categoriesByIdMap(server, categoryIds);

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

    private List<String> distinctCategoryIds(List<HomepageCard> cards, Predicate<HomepageCard> filter) {
        return cards.stream()
            .filter(filter)
            .map(HomepageCard::getCategoryId)
            .filter(id -> id != null && !id.isEmpty())
            .distinct()
            .toList();
    }

    private Map<String, KnowledgebaseCategory> categoriesByIdMap(Server server, List<String> categoryIds) {
        return categoryService.getCategoriesByIds(server, categoryIds)
            .stream()
            .collect(Collectors.toMap(KnowledgebaseCategory::getId, Function.identity()));
    }

    public record EmbeddedCategory(String id, String name, String slug) {}

    public record EnrichedCard(
        HomepageCard card,
        EmbeddedCategory category
    ) {}

    public record EnrichedCardWithArticles(
        HomepageCard card,
        KnowledgebaseCategory category,
        List<KnowledgebaseArticle> articles
    ) {}
}
