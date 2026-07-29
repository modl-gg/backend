package gg.modl.backend.replay.service;

import gg.modl.backend.database.mongo.repository.ReplayMongoRepository;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.validation.BeanValidationRunner;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.backend.replay.data.ReplayDocument;
import gg.modl.backend.replay.data.ReplayLabel;
import gg.modl.backend.replay.dto.InitReplayUploadResponse;
import gg.modl.backend.replay.dto.PublicReplayResponse;
import gg.modl.backend.replay.util.ReplayReferenceUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplayService {
    private final ReplayMongoRepository replayRepository;
    private final S3StorageService s3StorageService;
    private final StorageQuotaService storageQuotaService;
    private final TrainingDataService trainingDataService;
    private final BeanValidationRunner validationRunner;

    @Value("${modl.replay.max-file-size:10485760}")
    private long maxFileSize;

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

        String normalizedTargetUuid = ReplayReferenceUtil.requireValidUuid(targetUuid);
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
            StorageQuotaService.ConfirmResult confirmResult = storageQuotaService.confirmAndRecordFile(
                server, doc.getStorageKey(), doc.getFileSize(), "application/octet-stream");
            switch (confirmResult) {
                case SUCCESS -> log.debug("Replay {} confirmed for server {}", replayId, server.getDatabaseName());
                case QUOTA_EXCEEDED -> {
                    doc.setStatus(ReplayDocument.STATUS_FAILED);
                    replayRepository.saveEntity(server, doc);
                    throw new ValidationException("Storage quota exceeded");
                }
                case RECORD_FAILED -> {
                    doc.setStatus(ReplayDocument.STATUS_FAILED);
                    replayRepository.saveEntity(server, doc);
                    throw new ExternalServiceException("Failed to record upload");
                }
            }
        } else {
            log.warn("Replay {} upload not found in storage for server {}", replayId, server.getDatabaseName());
        }

        return exists;
    }

    public enum SubmitLabelsResult { OK, NOT_FOUND }

    public SubmitLabelsResult submitLabels(Server server, String replayId, List<ReplayLabel> labels) {
        validateLabels(labels);

        Optional<ReplayDocument> existing = replayRepository.findByReplayId(server, replayId);
        if (existing.isEmpty() || !ReplayDocument.STATUS_COMPLETE.equals(existing.get().getStatus())) {
            return SubmitLabelsResult.NOT_FOUND;
        }

        Optional<ReplayDocument> replaced = replayRepository.replaceLabels(server, replayId, labels);
        if (replaced.isEmpty()) {
            return SubmitLabelsResult.NOT_FOUND;
        }
        ReplayDocument doc = replaced.get();
        log.debug("Saved {} labels for replay {} on server {}", labels.size(), replayId, server.getDatabaseName());

        trainingDataService.generateSegmentsAsync(server, doc, labels);

        return SubmitLabelsResult.OK;
    }

    private void validateLabels(List<ReplayLabel> labels) {
        for (ReplayLabel label : labels) {
            validationRunner.validate(label);
        }
    }

    public Optional<PublicReplayResponse> getPublicReplay(Server server, String replayId) {
        return replayRepository.findByReplayId(server, replayId)
            .filter(doc -> ReplayDocument.STATUS_COMPLETE.equals(doc.getStatus()))
            .map(doc -> new PublicReplayResponse(
                doc.getId(),
                doc.getMcVersion(),
                doc.getFileSize(),
                doc.getCreatedAt() == null ? 0L : doc.getCreatedAt().getTime(),
                s3StorageService.getCdnUrl(doc.getStorageKey()),
                doc.getStatus(),
                doc.getLabels() != null && !doc.getLabels().isEmpty()
            ));
    }

    private String normalizeTargetName(String value) {
        String normalized = ReplayReferenceUtil.normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > RequestValidationLimits.LOG_USERNAME_MAX_LENGTH) {
            throw new ValidationException("Target name is too long");
        }
        return normalized;
    }
}
