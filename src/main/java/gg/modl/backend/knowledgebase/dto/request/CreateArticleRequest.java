package gg.modl.backend.knowledgebase.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateArticleRequest(
        @NotBlank String title,
        @NotBlank String content,
        Boolean isVisible
) {
}
