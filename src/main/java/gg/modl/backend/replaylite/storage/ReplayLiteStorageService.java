package gg.modl.backend.replaylite.storage;

import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.storage.config.S3ClientFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Slf4j
public class ReplayLiteStorageService {
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String DOWNLOAD_CACHE_CONTROL = "private, no-store, max-age=0";
    private static final Duration PRESIGN_UPLOAD_DURATION = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ReplayLiteStorageConfiguration configuration;

    @Autowired
    public ReplayLiteStorageService(ReplayLiteStorageConfiguration configuration) {
        this(createClient(configuration), createPresigner(configuration), configuration);
    }

    ReplayLiteStorageService(
        @Nullable S3Client s3Client,
        @Nullable S3Presigner s3Presigner,
        ReplayLiteStorageConfiguration configuration
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.configuration = configuration;
    }

    private static S3Client createClient(ReplayLiteStorageConfiguration configuration) {
        if (!configuration.isConfigured()) {
            return null;
        }

        return S3ClientFactory.createClient(
            configuration.getEndpoint(),
            configuration.getKeyId(),
            configuration.getApplicationKey()
        );
    }

    private static S3Presigner createPresigner(ReplayLiteStorageConfiguration configuration) {
        if (!configuration.isConfigured()) {
            return null;
        }

        return S3ClientFactory.createPresigner(
            configuration.getEndpoint(),
            configuration.getKeyId(),
            configuration.getApplicationKey()
        );
    }

    public boolean isConfigured() {
        return s3Client != null && s3Presigner != null && configuration.isConfigured();
    }

    public PresignedUpload createPresignedUpload(String objectKey, long contentLength) {
        if (!isConfigured()) {
            throw new ExternalServiceException("Replay Lite storage is not configured");
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(configuration.getBucketName())
            .key(objectKey)
            .contentType(CONTENT_TYPE)
            .contentLength(contentLength)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_UPLOAD_DURATION)
            .putObjectRequest(putRequest)
            .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", CONTENT_TYPE);

        return new PresignedUpload(
            presignedRequest.url().toString(),
            presignedRequest.httpRequest().method().name(),
            headers,
            Instant.now().plus(PRESIGN_UPLOAD_DURATION)
        );
    }

    public Optional<ObjectMetadata> headObject(String objectKey) {
        if (s3Client == null || !configuration.isConfigured()) {
            throw new ExternalServiceException("Replay Lite storage is not configured");
        }

        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(configuration.getBucketName())
                .key(objectKey)
                .build());
            return Optional.of(new ObjectMetadata(response.contentLength(), response.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public PresignedDownload createPresignedDownload(String objectKey, String downloadFilename, Duration duration) {
        if (!isConfigured()) {
            throw new ExternalServiceException("Replay Lite storage is not configured");
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(configuration.getBucketName())
            .key(objectKey)
            .responseCacheControl(DOWNLOAD_CACHE_CONTROL)
            .responseContentDisposition(ContentDisposition.inline().filename(downloadFilename).build().toString())
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(duration)
            .getObjectRequest(getRequest)
            .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return new PresignedDownload(presignedRequest.url().toString(), Instant.now().plus(duration));
    }

    public boolean deleteObject(String objectKey) {
        if (s3Client == null || !configuration.isConfigured()) {
            return false;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(configuration.getBucketName())
                .key(objectKey)
                .build());
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete Replay Lite object {}", objectKey, e);
            return false;
        }
    }

    public record PresignedUpload(
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt
    ) {}

    public record ObjectMetadata(long size, Instant lastModified) {}

    public record PresignedDownload(String url, Instant expiresAt) {}
}
