package gg.modl.backend.infrastructure.scheduling;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class KeysetDrainer {

    private KeysetDrainer() {
    }

    public static <T, C> long drain(
        int maxPages,
        int pageSize,
        PageFinder<T, C> finder,
        Function<List<T>, C> cursorExtractor,
        ToLongFunction<List<T>> pageConsumer
    ) {
        long total = 0;
        C cursor = null;
        int pages = 0;
        while (pages++ < maxPages) {
            List<T> page = finder.find(cursor, pageSize);
            if (page.isEmpty()) {
                break;
            }
            total += pageConsumer.applyAsLong(page);
            cursor = cursorExtractor.apply(page);
            if (page.size() < pageSize) {
                break;
            }
        }
        return total;
    }

    @FunctionalInterface
    public interface PageFinder<T, C> {
        List<T> find(C cursor, int limit);
    }
}
