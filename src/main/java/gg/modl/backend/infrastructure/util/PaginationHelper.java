package gg.modl.backend.infrastructure.util;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PaginationHelper {

    public int normalizeLimit(int limit, int maxLimit) {
        return Math.min(maxLimit, Math.max(1, limit));
    }

    public <T> PageResult<T> paginate(List<T> items, int page, int limit) {
        int totalCount = items.size();
        int safeLimit = Math.max(0, limit);
        int skip = calculateSkip(page, limit);
        int from = Math.min(skip, totalCount);
        int to = (int) Math.min((long) skip + safeLimit, totalCount);
        List<T> paged = items.subList(from, to);
        boolean hasMore = (long) skip + safeLimit < totalCount;
        return new PageResult<>(paged, totalCount, page, hasMore);
    }

    public int calculateSkip(int page, int limit) {
        long skip = (long) (normalizePage(page) - 1) * (long) Math.max(0, limit);
        return (int) Math.min(skip, Integer.MAX_VALUE);
    }

    public int normalizePage(int page) {
        return Math.max(1, page);
    }

    public int calculateTotalPages(long total, int limit) {
        return (int) Math.ceil((double) total / limit);
    }

    public record PageResult<T>(List<T> items, int totalCount, int page, boolean hasMore) {}
}
