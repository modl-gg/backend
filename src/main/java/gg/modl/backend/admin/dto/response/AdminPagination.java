package gg.modl.backend.admin.dto.response;

public record AdminPagination(
    int page,
    int limit,
    long total,
    int pages
) {
}
