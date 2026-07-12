package gg.modl.backend.storage.service;

import gg.modl.backend.infrastructure.util.DigestUtils;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.data.EvidenceUploadTokenDocument;
import gg.modl.backend.storage.repository.EvidenceUploadTokenMongoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvidenceUploadTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final EvidenceUploadTokenMongoRepository tokenRepository;

    public String createToken(Server server, String punishmentId, String playerUuid, String issuerName) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        tokenRepository.saveEntity(new EvidenceUploadTokenDocument(
            DigestUtils.sha256Hex(token),
            server.getDatabaseName(),
            punishmentId,
            playerUuid,
            issuerName,
            now,
            now.plus(TOKEN_TTL)
        ));
        return token;
    }

    public UploadToken validateToken(String token) {
        String hash = DigestUtils.sha256Hex(token);
        EvidenceUploadTokenDocument doc = tokenRepository.findByTokenHash(hash).orElse(null);
        if (doc == null) {
            return null;
        }
        if (doc.getExpiresAt() != null && doc.getExpiresAt().isBefore(Instant.now())) {
            tokenRepository.deleteByTokenHash(hash);
            return null;
        }
        return new UploadToken(
            token,
            doc.getServerDatabaseName(),
            doc.getPunishmentId(),
            doc.getPlayerUuid(),
            doc.getIssuerName(),
            doc.getCreatedAt()
        );
    }

    public void invalidateToken(String token) {
        tokenRepository.deleteByTokenHash(DigestUtils.sha256Hex(token));
    }


    public record UploadToken(
        String token,
        String serverDatabaseName,
        String punishmentId,
        String playerUuid,
        String issuerName,
        Instant createdAt
    ) {
    }
}
