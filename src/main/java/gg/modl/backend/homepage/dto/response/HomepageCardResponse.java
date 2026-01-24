package gg.modl.backend.homepage.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        @JsonProperty("icon_color") String iconColor,
        @JsonProperty("action_type") String actionType,
        @JsonProperty("action_url") String actionUrl,
        @JsonProperty("action_button_text") String actionButtonText,
        @JsonProperty("category_id") String categoryId,
        @JsonProperty("background_color") String backgroundColor,
        int ordinal,
        @JsonProperty("is_enabled") boolean isEnabled,
        @JsonProperty("created_at") Date createdAt,
        @JsonProperty("updated_at") Date updatedAt,
        EmbeddedCategory category
) {
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
}
