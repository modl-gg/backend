package gg.modl.backend.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class BucketPool {

    private static final int MAX_BUCKETS_PER_NAMESPACE = 50_000;

    private final Map<String, Cache<String, Bucket>> namespaces = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String namespace, String key, int capacity, Duration refillDuration) {
        return namespaces
            .computeIfAbsent(namespace, ignored -> Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS_PER_NAMESPACE)
                .<String, Bucket>build())
            .get(key, ignored -> createBucket(capacity, refillDuration));
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(capacity, refillDuration)
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
