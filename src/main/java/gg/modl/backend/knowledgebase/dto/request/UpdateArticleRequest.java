package gg.modl.backend.knowledgebase.dto.request;

public record UpdateArticleRequest(
        String title,
        String content,
        Boolean isVisible
) {
}
