package gg.modl.backend.knowledgebase.dto.request;

import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateArticleRequest(
    @NotBlank @Size(max = RequestValidationLimits.KB_ARTICLE_TITLE_MAX_LENGTH) String title,
    @NotBlank @Size(max = RequestValidationLimits.KB_ARTICLE_CONTENT_MAX_LENGTH) String content,
    @Nullable Boolean isVisible
) {
}
