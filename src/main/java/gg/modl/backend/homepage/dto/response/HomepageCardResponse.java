package gg.modl.backend.homepage.dto.response;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import java.util.Date;
import java.util.List;

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
    int ordinal,
    boolean isEnabled,
    Date createdAt,
    Date updatedAt,
    EmbeddedCategory category
) {
    public static HomepageCardResponse from(HomepageCard card) {
        return from(card, null);
    }

    public static HomepageCardResponse from(HomepageCard card, EmbeddedCategory category) {
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
            card.getOrdinal(),
            card.isEnabled(),
            card.getCreatedAt(),
            card.getUpdatedAt(),
            category
        );
    }

    public record EmbeddedCategory(
        String id,
        String name,
        String slug,
        String description,
        List<ArticleStub> articles
    ) {
        public static EmbeddedCategory from(KnowledgebaseCategory category, List<KnowledgebaseArticle> articles) {
            List<ArticleStub> articleStubs = articles.stream()
                .map(a -> new ArticleStub(a.getId(), a.getTitle(), a.getSlug(), a.getOrdinal()))
                .toList();
            return new EmbeddedCategory(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                articleStubs
            );
        }
    }

    public record ArticleStub(
        String id,
        String title,
        String slug,
        int ordinal
    ) {}
}
