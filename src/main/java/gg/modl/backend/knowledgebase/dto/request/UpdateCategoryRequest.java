package gg.modl.backend.knowledgebase.dto.request;

public record UpdateCategoryRequest(
        String name,
        String description,
        Boolean isVisible
) {
}
