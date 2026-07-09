package gg.modl.backend.homepage.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.homepage.data.HomepageCard;
import gg.modl.backend.homepage.dto.response.HomepageCardResponse;
import gg.modl.backend.homepage.service.HomepageCardService;
import gg.modl.proto.modl.v1.HomepageCardMutationResponse;
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

    public PublicHomepageCardsResponse toPublicCardsResponse(List<HomepageCardResponse> cards) {
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

    private gg.modl.proto.modl.v1.HomepageCardResponse toPublicCard(HomepageCardResponse card) {
        gg.modl.proto.modl.v1.HomepageCardResponse.Builder builder =
            gg.modl.proto.modl.v1.HomepageCardResponse.newBuilder()
                .setId(nullToEmpty(card.id()))
                .setTitle(nullToEmpty(card.title()))
                .setDescription(nullToEmpty(card.description()))
                .setIcon(nullToEmpty(card.icon()))
                .setIconColor(nullToEmpty(card.iconColor()))
                .setActionType(nullToEmpty(card.actionType()))
                .setActionUrl(nullToEmpty(card.actionUrl()))
                .setActionButtonText(nullToEmpty(card.actionButtonText()))
                .setCategoryId(nullToEmpty(card.categoryId()))
                .setBackgroundColor(nullToEmpty(card.backgroundColor()))
                .setOrdinal(card.ordinal())
                .setIsEnabled(card.isEnabled());
        if (card.createdAt() != null) {
            builder.setCreatedAt(toTimestamp(card.createdAt()));
        }
        if (card.updatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(card.updatedAt()));
        }
        HomepageCardResponse.EmbeddedCategory category = card.category();
        if (category != null) {
            gg.modl.proto.modl.v1.HomepageCardResponse.EmbeddedCategory.Builder embedded =
                gg.modl.proto.modl.v1.HomepageCardResponse.EmbeddedCategory.newBuilder()
                    .setId(nullToEmpty(category.id()))
                    .setName(nullToEmpty(category.name()))
                    .setSlug(nullToEmpty(category.slug()))
                    .setDescription(nullToEmpty(category.description()));
            addAll(category.articles(), this::toArticleStub, embedded::addArticles);
            builder.setCategory(embedded);
        }
        return builder.build();
    }

    private gg.modl.proto.modl.v1.HomepageCardResponse.ArticleStub toArticleStub(HomepageCardResponse.ArticleStub stub) {
        return gg.modl.proto.modl.v1.HomepageCardResponse.ArticleStub.newBuilder()
            .setId(nullToEmpty(stub.id()))
            .setTitle(nullToEmpty(stub.title()))
            .setSlug(nullToEmpty(stub.slug()))
            .setOrdinal(stub.ordinal())
            .build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
