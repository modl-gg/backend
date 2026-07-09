package gg.modl.backend.knowledgebase.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.addAll;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.setOptionalString;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.knowledgebase.data.KnowledgebaseArticle;
import gg.modl.backend.knowledgebase.data.KnowledgebaseCategory;
import gg.modl.proto.modl.v1.KnowledgebaseArticleResponse;
import gg.modl.proto.modl.v1.KnowledgebaseArticleStubResponse;
import gg.modl.proto.modl.v1.KnowledgebaseArticlesResponse;
import gg.modl.proto.modl.v1.KnowledgebaseCategoryResponse;
import gg.modl.proto.modl.v1.KnowledgebaseMessageResponse;
import gg.modl.proto.modl.v1.KnowledgebaseSearchResponse;
import gg.modl.proto.modl.v1.PanelKnowledgebaseCategoriesResponse;
import gg.modl.proto.modl.v1.PanelKnowledgebaseCategoryWithArticlesResponse;
import gg.modl.proto.modl.v1.PublicKnowledgebaseCategoriesResponse;
import gg.modl.proto.modl.v1.PublicKnowledgebaseCategoryWithArticlesResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgebaseProtoMapper {

    public KnowledgebaseArticleResponse toArticleResponse(KnowledgebaseArticle article) {
        KnowledgebaseArticleResponse.Builder builder = KnowledgebaseArticleResponse.newBuilder()
            .setId(nullToEmpty(article.getId()))
            .setTitle(nullToEmpty(article.getTitle()))
            .setSlug(nullToEmpty(article.getSlug()))
            .setContent(nullToEmpty(article.getContent()))
            .setCategoryId(nullToEmpty(article.getCategoryId()))
            .setOrdinal(article.getOrdinal())
            .setIsVisible(article.isVisible());
        if (article.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(article.getCreatedAt()));
        }
        if (article.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(article.getUpdatedAt()));
        }
        return builder.build();
    }

    public KnowledgebaseCategoryResponse toCategoryResponse(KnowledgebaseCategory category) {
        KnowledgebaseCategoryResponse.Builder builder = KnowledgebaseCategoryResponse.newBuilder()
            .setId(nullToEmpty(category.getId()))
            .setName(nullToEmpty(category.getName()))
            .setSlug(nullToEmpty(category.getSlug()))
            .setDescription(nullToEmpty(category.getDescription()))
            .setOrdinal(category.getOrdinal())
            .setIsVisible(category.isVisible());
        if (category.getCreatedAt() != null) {
            builder.setCreatedAt(toTimestamp(category.getCreatedAt()));
        }
        if (category.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(category.getUpdatedAt()));
        }
        return builder.build();
    }

    public KnowledgebaseArticlesResponse toArticlesResponse(List<KnowledgebaseArticle> articles) {
        KnowledgebaseArticlesResponse.Builder builder = KnowledgebaseArticlesResponse.newBuilder();
        addAll(articles, this::toArticleResponse, builder::addArticles);
        return builder.build();
    }

    public KnowledgebaseSearchResponse toSearchResponse(List<KnowledgebaseArticle> articles) {
        KnowledgebaseSearchResponse.Builder builder = KnowledgebaseSearchResponse.newBuilder();
        addAll(articles, this::toArticleResponse, builder::addArticles);
        return builder.build();
    }

    public PanelKnowledgebaseCategoriesResponse toPanelCategoriesResponse(
        List<KnowledgebaseCategory> categories,
        java.util.function.Function<KnowledgebaseCategory, List<KnowledgebaseArticle>> articlesForCategory
    ) {
        PanelKnowledgebaseCategoriesResponse.Builder builder = PanelKnowledgebaseCategoriesResponse.newBuilder();
        addAll(categories,
            category -> toPanelCategory(category, articlesForCategory.apply(category)),
            builder::addCategories);
        return builder.build();
    }

    public PublicKnowledgebaseCategoriesResponse toPublicCategoriesResponse(
        List<KnowledgebaseCategory> categories,
        java.util.function.Function<KnowledgebaseCategory, List<KnowledgebaseArticle>> articlesForCategory
    ) {
        PublicKnowledgebaseCategoriesResponse.Builder builder = PublicKnowledgebaseCategoriesResponse.newBuilder();
        addAll(categories,
            category -> toPublicCategory(category, articlesForCategory.apply(category)),
            builder::addCategories);
        return builder.build();
    }

    public KnowledgebaseMessageResponse message(String message) {
        return KnowledgebaseMessageResponse.newBuilder()
            .setMessage(nullToEmpty(message))
            .build();
    }

    private PanelKnowledgebaseCategoryWithArticlesResponse toPanelCategory(
        KnowledgebaseCategory category,
        List<KnowledgebaseArticle> articles
    ) {
        PanelKnowledgebaseCategoryWithArticlesResponse.Builder builder =
            PanelKnowledgebaseCategoryWithArticlesResponse.newBuilder()
                .setId(nullToEmpty(category.getId()))
                .setName(nullToEmpty(category.getName()))
                .setSlug(nullToEmpty(category.getSlug()))
                .setOrdinal(category.getOrdinal())
                .setIsVisible(category.isVisible());
        setOptionalString(builder::setDescription, category.getDescription());
        addAll(articles, this::toArticleResponse, builder::addArticles);
        return builder.build();
    }

    private PublicKnowledgebaseCategoryWithArticlesResponse toPublicCategory(
        KnowledgebaseCategory category,
        List<KnowledgebaseArticle> articles
    ) {
        PublicKnowledgebaseCategoryWithArticlesResponse.Builder builder =
            PublicKnowledgebaseCategoryWithArticlesResponse.newBuilder()
                .setId(nullToEmpty(category.getId()))
                .setName(nullToEmpty(category.getName()))
                .setSlug(nullToEmpty(category.getSlug()))
                .setOrdinal(category.getOrdinal());
        setOptionalString(builder::setDescription, category.getDescription());
        addAll(articles, this::toArticleStub, builder::addArticles);
        return builder.build();
    }

    private KnowledgebaseArticleStubResponse toArticleStub(KnowledgebaseArticle article) {
        return KnowledgebaseArticleStubResponse.newBuilder()
            .setId(nullToEmpty(article.getId()))
            .setTitle(nullToEmpty(article.getTitle()))
            .setSlug(nullToEmpty(article.getSlug()))
            .setOrdinal(article.getOrdinal())
            .build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
