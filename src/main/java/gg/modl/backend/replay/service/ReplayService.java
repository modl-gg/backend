package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.replay.dto.InitReplayUploadResponse;
import gg.modl.backend.replay.dto.PublicReplayResponse;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplayService {
    private final ReplayMongoRepository replayRepository;
    private final S3StorageService s3StorageService;
    private final StorageQuotaService storageQuotaService;
    private final TrainingDataService trainingDataService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public InitReplayUploadResponse initUpload(Server server, String mcVersion, long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum of 10 MB");
        }

        if (!storageQuotaService.canUpload(server, fileSize)) {
            throw new IllegalArgumentException("Storage quota exceeded");
        }

        String replayId = UUID.randomUUID().toString();

        PresignUploadResponse presign = s3StorageService.createPresignedUploadUrl(
            server, "replays", "replay.modlreplay", "application/octet-stream", fileSize
        );

        ReplayDocument doc = new ReplayDocument();
        doc.setId(replayId);
        doc.setMcVersion(mcVersion);
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
            log.debug("Replay {} confirmed for server {}", replayId, server.getDatabaseName());
        } else {
            log.warn("Replay {} upload not found in storage for server {}", replayId, server.getDatabaseName());
        }

        return exists;
    }

    public enum SubmitLabelsResult { OK, NOT_FOUND, ALREADY_LABELED }

    public SubmitLabelsResult submitLabels(Server server, String replayId, List<ReplayLabel> labels) {
        Optional<ReplayDocument> opt = replayRepository.findByReplayId(server, replayId);
        if (opt.isEmpty()) {
            return SubmitLabelsResult.NOT_FOUND;
        }

        ReplayDocument doc = opt.get();
        if (doc.getLabels() != null && !doc.getLabels().isEmpty()) {
            return SubmitLabelsResult.ALREADY_LABELED;
        }

        doc.setLabels(labels);
        replayRepository.saveEntity(server, doc);
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
}
