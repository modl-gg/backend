package gg.modl.backend.knowledgebase.dto.request;

import gg.modl.backend.validation.RequestValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CreateCategoryRequest(
    @NotBlank @Size(max = RequestValidationLimits.KB_CATEGORY_NAME_MAX_LENGTH) String name,
    @Nullable @Size(max = RequestValidationLimits.KB_CATEGORY_DESCRIPTION_MAX_LENGTH) String description
) {
}
