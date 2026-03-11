package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EvidenceUploadTokenService {

    private final ConcurrentHashMap<String, UploadToken> tokens = new ConcurrentHashMap<>();

    public String createToken(Server server, String punishmentId, String playerUuid, String issuerName) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new UploadToken(
            token,
            server.getDatabaseName(),
            punishmentId,
            playerUuid,
            issuerName,
            Instant.now()
        ));
        return token;
    }

    public UploadToken validateToken(String token) {
        UploadToken uploadToken = tokens.get(token);
        if (uploadToken == null) {
            return null;
        }
        if (uploadToken.isExpired()) {
            tokens.remove(token);
            return null;
        }
        return uploadToken;
    }

    public void invalidateToken(String token) {
        tokens.remove(token);
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredTokens() {
        tokens.entrySet().removeIf(stringUploadTokenEntry -> stringUploadTokenEntry.getValue().isExpired());
    }

    public record UploadToken(
        String token,
        String serverDatabaseName,
        String punishmentId,
        String playerUuid,
        String issuerName,
        Instant createdAt
    ) {
        private static final long TTL_MINUTES = 30;

        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(TTL_MINUTES * 60));
        }
    }
}
