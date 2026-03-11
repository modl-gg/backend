package gg.modl.backend.util;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PaginationHelper {

    public int normalizeLimit(int limit, int maxLimit) {
        return Math.min(maxLimit, Math.max(1, limit));
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

    public int calculateSkip(int page, int limit) {
        return (normalizePage(page) - 1) * limit;
    }

    public int normalizePage(int page) {
        return Math.max(1, page);
    }

    public record PageResult<T>(List<T> items, int totalCount, int page, boolean hasMore) {}
}
