package gg.modl.backend.storage.service;

import gg.modl.backend.infrastructure.util.ByteFormatUtil;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Service
@RequiredArgsConstructor
public class UploadOrchestrationService {
    private final MediaValidationService validationService;
    private final StorageQuotaService quotaService;
    private final S3StorageService s3StorageService;
    private final StorageMetadataService storageMetadataService;
    private final ServerLimitPolicy serverLimitPolicy;
    private final TempUploadReclamationService reclamationService;

    @Value("${modl.storage.temp-upload.max-bytes:512MB}")
    private DataSize anonymousTempUploadCap;

    @Value("${modl.storage.temp-upload.window:PT6H}")
    private Duration anonymousTempUploadWindow;

    public PresignOutcome presign(Server server, UploadPresignRequest request) {
        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
            request.fileName(),
            request.contentType(),
            request.fileSize(),
            request.uploadType(),
            request.premium()
        );
        if (!validation.valid()) {
            return PresignOutcome.failed(PresignStatus.VALIDATION_FAILED, validation.error());
        }

        ServerLimits limits = serverLimitPolicy.resolve(server);
        if (limits.exceedsUploadLimit(request.fileSize())) {
            return PresignOutcome.failed(PresignStatus.VALIDATION_FAILED,
                "File exceeds maximum size of " + ByteFormatUtil.formatCompact(limits.getMaxUploadBytes()));
        }

        if (request.enforceTempCap() && exceedsTempBudget(server, request.fileSize())) {
            return PresignOutcome.failed(PresignStatus.TEMP_LIMIT_EXCEEDED, null);
        }

        if (!quotaService.canUpload(server, request.fileSize())) {
            return PresignOutcome.failed(PresignStatus.QUOTA_EXCEEDED, "Storage quota exceeded");
        }

        PresignUploadResponse response = s3StorageService.createPresignedUploadUrl(
            server,
            request.uploadType(),
            request.fileName(),
            request.contentType(),
            request.fileSize(),
            request.entityId()
        );
        return PresignOutcome.success(response);
    }

    public ConfirmOutcome confirm(Server server, String key, boolean enforceTempCap) {
        validationService.assertKeyOwnedByServer(server, key);

        UploadResponse details = s3StorageService.getUploadDetails(key);
        if (details == null) {
            return ConfirmOutcome.status(ConfirmStatus.UPLOAD_NOT_FOUND);
        }

        if (enforceTempCap && exceedsTempBudget(server, details.size())) {
            storageMetadataService.cleanupOrphanedUpload(server, key, details.size(), details.contentType());
            return ConfirmOutcome.status(ConfirmStatus.TEMP_LIMIT_EXCEEDED);
        }

        StorageQuotaService.ConfirmResult result =
            quotaService.confirmAndRecordFile(server, key, details.size(), details.contentType());
        switch (result) {
            case QUOTA_EXCEEDED -> {
                storageMetadataService.cleanupOrphanedUpload(server, key, details.size(), details.contentType());
                return ConfirmOutcome.status(ConfirmStatus.QUOTA_EXCEEDED);
            }
            case RECORD_FAILED -> {
                storageMetadataService.cleanupOrphanedUpload(server, key, details.size(), details.contentType());
                return ConfirmOutcome.status(ConfirmStatus.RECORD_FAILED);
            }
            default -> {
            }
        }

        if (enforceTempCap) {
            reclamationService.reclaimAsync(server);
        }
        return ConfirmOutcome.success(details);
    }

    private boolean exceedsTempBudget(Server server, long additionalBytes) {
        if (additionalBytes < 0) {
            return true;
        }
        Date windowStart = Date.from(Instant.now().minus(anonymousTempUploadWindow));
        long used = storageMetadataService.sumTempUploadBytes(server, windowStart);
        return used + additionalBytes > anonymousTempUploadCap.toBytes();
    }

    public record UploadPresignRequest(
        String uploadType,
        String fileName,
        String contentType,
        long fileSize,
        String entityId,
        boolean premium,
        boolean enforceTempCap
    ) {}

    public enum PresignStatus {
        SUCCESS,
        VALIDATION_FAILED,
        QUOTA_EXCEEDED,
        TEMP_LIMIT_EXCEEDED
    }

    public record PresignOutcome(PresignStatus status, String message, PresignUploadResponse upload) {
        static PresignOutcome success(PresignUploadResponse upload) {
            return new PresignOutcome(PresignStatus.SUCCESS, null, upload);
        }

        static PresignOutcome failed(PresignStatus status, String message) {
            return new PresignOutcome(status, message, null);
        }
    }

    public enum ConfirmStatus {
        SUCCESS,
        UPLOAD_NOT_FOUND,
        QUOTA_EXCEEDED,
        TEMP_LIMIT_EXCEEDED,
        RECORD_FAILED
    }

    public record ConfirmOutcome(ConfirmStatus status, UploadResponse upload) {
        static ConfirmOutcome success(UploadResponse upload) {
            return new ConfirmOutcome(ConfirmStatus.SUCCESS, upload);
        }

        static ConfirmOutcome status(ConfirmStatus status) {
            return new ConfirmOutcome(status, null);
        }
    }
}
