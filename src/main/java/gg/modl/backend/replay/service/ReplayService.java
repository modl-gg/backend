package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.replay.dto.InitReplayUploadResponse;
import gg.modl.backend.replay.dto.PlayerReplayResponse;
import gg.modl.backend.replay.dto.PublicReplayResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageMetadataService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.backend.ticket.data.Ticket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReplayService {
    private final ReplayMongoRepository replayRepository;
    private final S3StorageService s3StorageService;
    private final StorageQuotaService storageQuotaService;
    private final TrainingDataService trainingDataService;
    private final StorageMetadataService storageMetadataService;
    private final TicketMongoRepository ticketRepository;

    @Value("${modl.replay.max-file-size:10485760}")
    private long maxFileSize;

    public ReplayService(ReplayMongoRepository replayRepository, S3StorageService s3StorageService,
                         StorageQuotaService storageQuotaService, TrainingDataService trainingDataService,
                         StorageMetadataService storageMetadataService, TicketMongoRepository ticketRepository) {
        this.replayRepository = replayRepository;
        this.s3StorageService = s3StorageService;
        this.storageQuotaService = storageQuotaService;
        this.trainingDataService = trainingDataService;
        this.storageMetadataService = storageMetadataService;
        this.ticketRepository = ticketRepository;
    }

    public InitReplayUploadResponse initUpload(Server server, String mcVersion, long fileSize) {
        return initUpload(server, mcVersion, fileSize, null, null);
    }

    public InitReplayUploadResponse initUpload(
        Server server,
        String mcVersion,
        long fileSize,
        String targetUuid,
        String targetName
    ) {
        if (fileSize > maxFileSize) {
            throw new ValidationException("File size exceeds maximum of " + (maxFileSize / 1024 / 1024) + " MB");
        }

        String normalizedTargetUuid = normalizeTargetUuid(targetUuid);
        String normalizedTargetName = normalizeTargetName(targetName);

        if (!storageQuotaService.canUpload(server, fileSize)) {
            throw new ValidationException("Storage quota exceeded");
        }

        String replayId = UUID.randomUUID().toString();

        PresignUploadResponse presign = s3StorageService.createPresignedUploadUrl(
            server, "replays", "replay.modlreplay", "application/octet-stream", fileSize
        );

        ReplayDocument doc = new ReplayDocument();
        doc.setId(replayId);
        doc.setMcVersion(mcVersion);
        doc.setTargetUuid(normalizedTargetUuid);
        doc.setTargetName(normalizedTargetName);
        doc.setFileSize(fileSize);
        doc.setStorageKey(presign.key());
        doc.setStatus(ReplayDocument.STATUS_PENDING);
        doc.setCreatedAt(new Date());

        replayRepository.saveEntity(server, doc);

        return new InitReplayUploadResponse(
            replayId,
            presign.presignedUrl(),
            presign.method(),
            presign.requiredHeaders()
        );
    }

    public boolean confirmUpload(Server server, String replayId) {
        Optional<ReplayDocument> opt = replayRepository.findByReplayId(server, replayId);
        if (opt.isEmpty()) {
            return false;
        }

        ReplayDocument doc = opt.get();
        boolean exists = s3StorageService.verifyUploadExists(doc.getStorageKey());
        doc.setStatus(exists ? ReplayDocument.STATUS_COMPLETE : ReplayDocument.STATUS_FAILED);
        replayRepository.saveEntity(server, doc);

        if (exists) {
            if (!storageQuotaService.confirmAndRecordFile(server, doc.getStorageKey(), doc.getFileSize(), "application/octet-stream")) {
                doc.setStatus(ReplayDocument.STATUS_FAILED);
                replayRepository.saveEntity(server, doc);
                throw new ValidationException("Storage quota exceeded");
            }
            log.debug("Replay {} confirmed for server {}", replayId, server.getDatabaseName());
        } else {
            log.warn("Replay {} upload not found in storage for server {}", replayId, server.getDatabaseName());
        }

        return exists;
    }

    public enum SubmitLabelsResult { OK, NOT_FOUND, ALREADY_LABELED }

    public SubmitLabelsResult submitLabels(Server server, String replayId, List<ReplayLabel> labels) {
        Optional<ReplayDocument> claimed = replayRepository.claimLabels(server, replayId, labels);
        if (claimed.isEmpty()) {
            return replayRepository.findByReplayId(server, replayId).isPresent()
                   ? SubmitLabelsResult.ALREADY_LABELED
                   : SubmitLabelsResult.NOT_FOUND;
        }
        ReplayDocument doc = claimed.get();
        log.debug("Saved {} labels for replay {} on server {}", labels.size(), replayId, server.getDatabaseName());

        trainingDataService.generateSegmentsAsync(server, doc, labels);

        return SubmitLabelsResult.OK;
    }

    public Optional<PublicReplayResponse> getPublicReplay(Server server, String replayId) {
        return replayRepository.findByReplayId(server, replayId)
            .filter(doc -> ReplayDocument.STATUS_COMPLETE.equals(doc.getStatus()))
            .map(doc -> new PublicReplayResponse(
                doc.getId(),
                doc.getMcVersion(),
                doc.getFileSize(),
                doc.getCreatedAt().getTime(),
                s3StorageService.getCdnUrl(doc.getStorageKey()),
                doc.getStatus(),
                doc.getLabels() != null && !doc.getLabels().isEmpty()
            ));
    }

    public List<PlayerReplayResponse> listPlayerReplays(Server server, String playerUuid) {
        String normalizedPlayerUuid = normalizeTargetUuid(playerUuid);
        Map<String, PlayerReplayResponse> responses = new LinkedHashMap<>();

        List<ReplayDocument> directReplays = replayRepository.findByTargetUuid(server, normalizedPlayerUuid, 100);
        for (ReplayDocument replay : directReplays) {
            PlayerReplayResponse response = toPlayerReplayResponse(replay, PlayerReplayResponse.MatchSource.DIRECT_METADATA);
            responses.put(response.deduplicationKey(), response);
        }

        List<Ticket> tickets = ticketRepository.findPlayerTicketsWithReplayUrl(server, normalizedPlayerUuid, 100);
        for (Ticket ticket : tickets) {
            String replayUrl = normalizeOptional(ticket.getReplayUrl());
            if (replayUrl == null) {
                continue;
            }
            String replayId = extractReplayId(replayUrl);
            if (hasReplayReference(responses, replayUrl, replayId)) {
                continue;
            }
            String key = replayId != null ? replayIdKey(replayId) : replayUrlKey(replayUrl);
            if (responses.containsKey(key)) {
                continue;
            }
            responses.put(key, PlayerReplayResponse.fromTicket(ticket, replayUrl, replayId));
        }

        return List.copyOf(responses.values());
    }

    private PlayerReplayResponse toPlayerReplayResponse(ReplayDocument replay, PlayerReplayResponse.MatchSource matchSource) {
        String replayUrl = ReplayDocument.STATUS_COMPLETE.equals(replay.getStatus()) && replay.getStorageKey() != null
                           ? s3StorageService.getCdnUrl(replay.getStorageKey())
                           : null;
        return new PlayerReplayResponse(
            replay.getId(),
            replay.getTargetUuid(),
            replay.getTargetName(),
            replay.getMcVersion(),
            replay.getFileSize(),
            replay.getCreatedAt(),
            replay.getStatus(),
            replayUrl,
            matchSource
        );
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTargetUuid(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Target UUID must be a valid UUID");
        }
    }

    private String normalizeTargetName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) {
            throw new ValidationException("Target name is too long");
        }
        return normalized;
    }

    private String replayUrlKey(String replayUrl) {
        return "url:" + replayUrl;
    }

    private String replayIdKey(String replayId) {
        return "id:" + replayId;
    }

    private boolean hasReplayReference(Map<String, PlayerReplayResponse> responses, String replayUrl, String replayId) {
        return responses.values()
            .stream()
            .anyMatch(response ->
                replayUrl.equals(response.replayUrl())
                || (replayId != null && replayId.equals(response.replayId()))
            );
    }

    private String extractReplayId(String replayReference) {
        String normalized = normalizeOptional(replayReference);
        if (normalized == null) {
            return null;
        }
        if (isRawReplayId(normalized)) {
            return normalized;
        }
        try {
            URI uri = new URI(normalized);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return null;
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                String key = separator >= 0 ? pair.substring(0, separator) : pair;
                if (!"id".equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                    continue;
                }
                String value = separator >= 0 ? pair.substring(separator + 1) : "";
                return normalizeOptional(URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
            return null;
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return null;
        }
    }

    private boolean isRawReplayId(String replayReference) {
        return !replayReference.contains("://")
               && !replayReference.contains("/")
               && !replayReference.contains("?")
               && !replayReference.contains("#");
    }
}
