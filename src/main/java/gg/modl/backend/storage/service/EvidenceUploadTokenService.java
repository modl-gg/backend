package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.data.EvidenceUploadTokenDocument;
import gg.modl.backend.storage.repository.EvidenceUploadTokenMongoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
            sha256Hex(token),
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
        String hash = sha256Hex(token);
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
        tokenRepository.deleteByTokenHash(sha256Hex(token));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
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
