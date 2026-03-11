package gg.modl.backend.util;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class PaginationHelper {

    public int normalizePage(int page) {
        return Math.max(1, page);
    }

    public int normalizeLimit(int limit, int maxLimit) {
        return Math.min(maxLimit, Math.max(1, limit));
    }

    public int calculateSkip(int page, int limit) {
        return (normalizePage(page) - 1) * limit;
    }

    public <T> PageResult<T> paginate(List<T> items, int page, int limit) {
        int totalCount = items.size();
        int skip = calculateSkip(page, limit);
        List<T> paged = skip >= totalCount
                ? List.of()
                : items.subList(skip, Math.min(skip + limit, totalCount));
        boolean hasMore = skip + limit < totalCount;
        return new PageResult<>(paged, totalCount, page, hasMore);
    }

    public record PageResult<T>(List<T> items, int totalCount, int page, boolean hasMore) {}
}
