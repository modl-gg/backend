package gg.modl.backend.homepage.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.nullToEmpty;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.proto.modl.v1.HomepageCardMutationResponse;
import gg.modl.proto.modl.v1.HomepageCardResponse;
import gg.modl.proto.modl.v1.PanelHomepageCardResponse;
import gg.modl.proto.modl.v1.PanelHomepageCardsResponse;
import gg.modl.proto.modl.v1.PublicHomepageCardsResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HomepageProtoMapper {

    public PanelHomepageCardsResponse toPanelCardsResponse(List<HomepageCardService.EnrichedCard> cards) {
        PanelHomepageCardsResponse.Builder builder = PanelHomepageCardsResponse.newBuilder();
        addAll(cards, this::toPanelCard, builder::addCards);
        return builder.build();
    }

    public PanelHomepageCardResponse toPanelCardResponse(HomepageCard card) {
        return panelCardBuilder(card).build();
    }

    public PublicHomepageCardsResponse toPublicCardsResponse(List<HomepageCardService.EnrichedCardWithArticles> cards) {
        PublicHomepageCardsResponse.Builder builder = PublicHomepageCardsResponse.newBuilder();
        addAll(cards, this::toPublicCard, builder::addCards);
        return builder.build();
    }

    public HomepageCardMutationResponse message(String message) {
        return HomepageCardMutationResponse.newBuilder()
            .setMessage(nullToEmpty(message))
            .build();
    }

    private PanelHomepageCardResponse toPanelCard(HomepageCardService.EnrichedCard enriched) {
        PanelHomepageCardResponse.Builder builder = panelCardBuilder(enriched.card());
        HomepageCardService.EmbeddedCategory category = enriched.category();
        if (category != null) {
            builder.setCategory(PanelHomepageCardResponse.EmbeddedCategory.newBuilder()
                .setId(nullToEmpty(category.id()))
                .setName(nullToEmpty(category.name()))
                .setSlug(nullToEmpty(category.slug())));
        }
        return builder.build();
    }

    private PanelHomepageCardResponse.Builder panelCardBuilder(HomepageCard card) {
        PanelHomepageCardResponse.Builder builder = PanelHomepageCardResponse.newBuilder()
            .setId(nullToEmpty(card.getId()))
            .setTitle(nullToEmpty(card.getTitle()))
            .setDescription(nullToEmpty(card.getDescription()))
            .setIcon(nullToEmpty(card.getIcon()))
            .setIconColor(nullToEmpty(card.getIconColor()))
            .setActionType(nullToEmpty(card.getActionType()))
            .setActionUrl(nullToEmpty(card.getActionUrl()))
            .setActionButtonText(nullToEmpty(card.getActionButtonText()))
            .setCategoryId(nullToEmpty(card.getCategoryId()))
            .setBackgroundColor(nullToEmpty(card.getBackgroundColor()))
            .setIsEnabled(card.isEnabled())
            .setOrdinal(card.getOrdinal());
        if (card.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(card.getCreatedAt()));
        }
        if (card.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(card.getUpdatedAt()));
        }
        return builder;
    }

    private HomepageCardResponse toPublicCard(HomepageCardService.EnrichedCardWithArticles enriched) {
        HomepageCard card = enriched.card();
        HomepageCardResponse.Builder builder = HomepageCardResponse.newBuilder()
            .setId(nullToEmpty(card.getId()))
            .setTitle(nullToEmpty(card.getTitle()))
            .setDescription(nullToEmpty(card.getDescription()))
            .setIcon(nullToEmpty(card.getIcon()))
            .setIconColor(nullToEmpty(card.getIconColor()))
            .setActionType(nullToEmpty(card.getActionType()))
            .setActionUrl(nullToEmpty(card.getActionUrl()))
            .setActionButtonText(nullToEmpty(card.getActionButtonText()))
            .setCategoryId(nullToEmpty(card.getCategoryId()))
            .setBackgroundColor(nullToEmpty(card.getBackgroundColor()))
            .setOrdinal(card.getOrdinal())
            .setIsEnabled(card.isEnabled());
        if (card.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(card.getCreatedAt()));
        }
        if (card.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(card.getUpdatedAt()));
        }
        KnowledgebaseCategory category = enriched.category();
        if (category != null) {
            HomepageCardResponse.EmbeddedCategory.Builder embedded =
                HomepageCardResponse.EmbeddedCategory.newBuilder()
                    .setId(nullToEmpty(category.getId()))
                    .setName(nullToEmpty(category.getName()))
                    .setSlug(nullToEmpty(category.getSlug()))
                    .setDescription(nullToEmpty(category.getDescription()));
            addAll(enriched.articles(), this::toArticleStub, embedded::addArticles);
            builder.setCategory(embedded);
        }
        return builder.build();
    }

    private HomepageCardResponse.ArticleStub toArticleStub(KnowledgebaseArticle article) {
        return HomepageCardResponse.ArticleStub.newBuilder()
            .setId(nullToEmpty(article.getId()))
            .setTitle(nullToEmpty(article.getTitle()))
            .setSlug(nullToEmpty(article.getSlug()))
            .setOrdinal(article.getOrdinal())
            .build();
    }
}
