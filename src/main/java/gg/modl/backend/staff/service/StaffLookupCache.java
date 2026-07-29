package gg.modl.backend.staff.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffLookupCache {
    private final StaffMongoRepository staffRepository;

    private final Cache<String, Optional<Staff>> staffByEmailCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(2))
        .build();

    public Optional<Staff> findByEmail(Server server, String email) {
        String normalized = EmailAddressUtil.normalize(email);
        if (normalized == null) {
            return Optional.empty();
        }
        String cacheKey = server.getId() + ":" + normalized;
        return staffByEmailCache.get(cacheKey, key ->
            staffRepository.findByEmailIgnoreCase(server, email)
        );
    }

    public void evict(Server server, String email) {
        String normalized = EmailAddressUtil.normalize(email);
        if (normalized == null) {
            return;
        }
        staffByEmailCache.invalidate(server.getId() + ":" + normalized);
    }

    public void evictAll() {
        staffByEmailCache.invalidateAll();
    }
}
