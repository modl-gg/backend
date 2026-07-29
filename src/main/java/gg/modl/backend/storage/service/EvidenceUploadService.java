package gg.modl.backend.storage.service;

import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.service.PlayerDataUtils;
import gg.modl.backend.player.service.PunishmentEvidenceService;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationResult;
import gg.modl.backend.player.service.PunishmentQueryService.PunishmentOperationStatus;
import gg.modl.backend.player.service.PunishmentQueryService.UploadedEvidenceItem;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.dto.request.EvidenceConfirmUploadRequest;
import gg.modl.backend.storage.dto.request.EvidenceItemRequest;
import gg.modl.backend.storage.dto.request.EvidencePresignUploadRequest;
import gg.modl.backend.storage.dto.request.SubmitEvidenceRequest;
import gg.modl.backend.storage.data.StorageFileDocument;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvidenceUploadService {
    private final EvidenceUploadTokenService tokenService;
    private final S3StorageService s3StorageService;
    private final PlayerMongoRepository playerRepository;
    private final ServerService serverService;
    private final MediaValidationService validationService;
    private final PunishmentEvidenceService punishmentEvidenceService;
    private final StorageMetadataService storageMetadataService;
    private final UploadOrchestrationService uploadOrchestrationService;

    public TokenValidationResult validateToken(String token) {
        EvidenceUploadTokenService.UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return TokenValidationResult.invalid();
        }

        Server server = serverService.getServerByDatabaseName(uploadToken.serverDatabaseName());
        Player player = server == null
            ? null
            : playerRepository.findByMinecraftUuid(server, UuidUtils.normalize(uploadToken.playerUuid())).orElse(null);
        String playerName = player != null ? PlayerDataUtils.extractLatestUsername(player.getUsernames()) : "Unknown";

        return TokenValidationResult.valid(new TokenInfo(
            uploadToken.punishmentId(),
            playerName,
            uploadToken.issuerName()
        ));
    }

    public PresignUploadResult presignUpload(String token, EvidencePresignUploadRequest request) {
        EvidenceUploadTokenService.UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return new PresignUploadResult.InvalidToken();
        }

        if (!s3StorageService.isConfigured()) {
            return new PresignUploadResult.StorageNotConfigured();
        }

        Server server = serverService.getServerByDatabaseName(uploadToken.serverDatabaseName());
        if (server == null) {
            return new PresignUploadResult.ServerNotFound();
        }

        UploadOrchestrationService.PresignOutcome outcome = uploadOrchestrationService.presign(server,
            new UploadOrchestrationService.UploadPresignRequest(
                "evidence",
                request.fileName(),
                request.contentType(),
                request.fileSize(),
                uploadToken.punishmentId(),
                server.getPlan() == ServerPlan.PREMIUM,
                false
            ));

        return new PresignUploadResult.Orchestrated(outcome);
    }

    public ConfirmUploadResult confirmUpload(String token, EvidenceConfirmUploadRequest request) {
        EvidenceUploadTokenService.UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return new ConfirmUploadResult.InvalidToken();
        }

        if (!validationService.isKeyOwnedByServer(request.key(), uploadToken.serverDatabaseName())) {
            return new ConfirmUploadResult.InvalidKey();
        }

        String expectedKeyPrefix = uploadToken.serverDatabaseName() + "/evidence/" + uploadToken.punishmentId() + "/";
        if (!request.key().startsWith(expectedKeyPrefix)) {
            return new ConfirmUploadResult.InvalidKey();
        }

        Server server = serverService.getServerByDatabaseName(uploadToken.serverDatabaseName());
        if (server == null) {
            UploadResponse uploadDetails = s3StorageService.getUploadDetails(request.key());
            if (uploadDetails == null) {
                return new ConfirmUploadResult.Orchestrated(
                    UploadOrchestrationService.ConfirmOutcome.status(UploadOrchestrationService.ConfirmStatus.UPLOAD_NOT_FOUND));
            }
            log.warn("Could not record storage metadata: server not found for database {}", uploadToken.serverDatabaseName());
            return new ConfirmUploadResult.Orchestrated(UploadOrchestrationService.ConfirmOutcome.success(uploadDetails));
        }

        UploadOrchestrationService.ConfirmOutcome outcome =
            uploadOrchestrationService.confirm(server, request.key(), false);
        return new ConfirmUploadResult.Orchestrated(outcome);
    }

    public SubmitEvidenceResult submitEvidence(String token, SubmitEvidenceRequest request) {
        EvidenceUploadTokenService.UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return new SubmitEvidenceResult.InvalidToken();
        }

        Server server = serverService.getServerByDatabaseName(uploadToken.serverDatabaseName());
        if (server == null) {
            return new SubmitEvidenceResult.ServerNotFound();
        }

        List<EvidenceItemRequest> items = request.evidence();
        List<String> keys = new ArrayList<>(items.size());
        for (EvidenceItemRequest item : items) {
            if (!isAllowedEvidenceUrl(item.url(), uploadToken)) {
                return new SubmitEvidenceResult.InvalidUrl();
            }

            String key = extractKeyFromEvidenceUrl(item.url());
            if (key == null) {
                return new SubmitEvidenceResult.InvalidUrl();
            }
            keys.add(key);
        }

        Map<String, StorageFileDocument> confirmedFiles = storageMetadataService.findConfirmedFiles(server, keys);

        List<UploadedEvidenceItem> evidenceItems = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            EvidenceItemRequest item = items.get(i);
            StorageFileDocument doc = confirmedFiles.get(keys.get(i));
            if (doc == null) {
                return new SubmitEvidenceResult.InvalidUrl();
            }

            evidenceItems.add(new UploadedEvidenceItem(
                item.url(),
                item.fileName(),
                doc.getContentType(),
                doc.getSize()
            ));
        }

        PunishmentOperationResult result = punishmentEvidenceService.addUploadedEvidence(
            server,
            uploadToken.punishmentId(),
            uploadToken.issuerName(),
            null,
            evidenceItems
        );
        if (result.status() == PunishmentOperationStatus.NOT_FOUND) {
            return new SubmitEvidenceResult.PunishmentNotFound(result.message());
        }

        tokenService.invalidateToken(token);
        return new SubmitEvidenceResult.Success();
    }

    private String extractKeyFromEvidenceUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isAllowedEvidenceUrl(String url, EvidenceUploadTokenService.UploadToken uploadToken) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                return false;
            }

            String expectedPathFragment = "/" + uploadToken.serverDatabaseName() + "/evidence/" + uploadToken.punishmentId() + "/";
            String path = uri.getPath();
            if (path == null || !path.contains(expectedPathFragment)) {
                return false;
            }

            String cdnDomain = s3StorageService.getCdnDomain();
            return cdnDomain != null && !cdnDomain.isBlank() && cdnDomain.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record TokenValidationResult(boolean valid, TokenInfo info) {
        private static TokenValidationResult invalid() {
            return new TokenValidationResult(false, null);
        }

        private static TokenValidationResult valid(TokenInfo info) {
            return new TokenValidationResult(true, info);
        }
    }

    public record TokenInfo(String punishmentId, String playerName, String issuerName) {
    }

    public sealed interface PresignUploadResult {
        record InvalidToken() implements PresignUploadResult {}

        record StorageNotConfigured() implements PresignUploadResult {}

        record ServerNotFound() implements PresignUploadResult {}

        record Orchestrated(UploadOrchestrationService.PresignOutcome outcome) implements PresignUploadResult {}
    }

    public sealed interface ConfirmUploadResult {
        record InvalidToken() implements ConfirmUploadResult {}

        record InvalidKey() implements ConfirmUploadResult {}

        record Orchestrated(UploadOrchestrationService.ConfirmOutcome outcome) implements ConfirmUploadResult {}
    }

    public sealed interface SubmitEvidenceResult {
        record InvalidToken() implements SubmitEvidenceResult {}

        record ServerNotFound() implements SubmitEvidenceResult {}

        record InvalidUrl() implements SubmitEvidenceResult {}

        record PunishmentNotFound(String message) implements SubmitEvidenceResult {}

        record Success() implements SubmitEvidenceResult {}
    }
}
