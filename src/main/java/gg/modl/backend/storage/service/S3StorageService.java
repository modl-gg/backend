package gg.modl.backend.storage.service;

import gg.modl.backend.infrastructure.exception.ExternalServiceException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.config.S3Configuration;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Slf4j
public class S3StorageService {
    public record UploadFileResult(String key, String cdnUrl) {}
    public record S3ObjectInfo(String key, long size, Instant lastModified) {}
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Configuration s3Configuration;
    private static final Duration PRESIGN_UPLOAD_DURATION = Duration.ofMinutes(15);

    public S3StorageService(
        @Nullable S3Client s3Client,
        @Nullable S3Presigner s3Presigner,
        S3Configuration s3Configuration
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Configuration = s3Configuration;
        if (s3Client == null) {
            log.warn("S3 storage is not configured. File storage features will be disabled.");
        }
    }

    public byte[] downloadBytes(String key) {
        if (s3Client == null) {
            throw new IllegalStateException("S3 not configured");
        }
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(s3Configuration.getBucketName()).key(key).build());
        return response.asByteArray();
    }

    public boolean isConfigured() {
        return s3Client != null && s3Configuration.getBucketName() != null && !s3Configuration.getBucketName().isBlank();
    }

    public String getCdnDomain() {
        return s3Configuration.getCdnDomain();
    }

    public boolean deleteFile(String key) {
        if (s3Client == null) {
            return false;
        }

        try {
            List<ObjectIdentifier> toDelete = collectAllVersions(key);

            if (toDelete.isEmpty()) {
                return true;
            }

            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .delete(Delete.builder().objects(toDelete).quiet(true).build())
                .build();

            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            if (response.hasErrors()) {
                log.warn("Failed to delete one or more S3 object versions for key {}: {}", key, describeDeleteErrors(response.errors()));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Error deleting file: {}", key, e);
            return false;
        }
    }

    public List<StorageFileResponse> listFiles(Server server, String prefix) {
        if (s3Client == null) {
            return Collections.emptyList();
        }

        String fullPrefix = server.getDatabaseName() + "/" + (prefix != null ? prefix : "");

        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(s3Configuration.getBucketName())
            .prefix(fullPrefix)
            .maxKeys(1000)
            .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents()
            .stream()
            .map(obj -> new StorageFileResponse(
                obj.key(),
                StorageKeyUtils.extractFileName(obj.key()),
                obj.size(),
                "application/octet-stream",
                Date.from(obj.lastModified()),
                getCdnUrl(obj.key())
            ))
            .toList();
    }

    public String getCdnUrl(String key) {
        String cdn = s3Configuration.getCdnDomain();
        if (cdn == null || cdn.isBlank()) {
            return getPresignedUrl(key);
        }
        return String.format("https://%s/%s", cdn, key);
    }

    public String getPresignedUrl(String key) {
        if (s3Presigner == null) {
            return null;
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .key(key)
                .build())
            .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public PresignUploadResponse createPresignedUploadUrl(
        Server server,
        String uploadType,
        String fileName,
        String contentType,
        long fileSize
    ) {
        return createPresignedUploadUrl(server, uploadType, fileName, contentType, fileSize, null);
    }

    public PresignUploadResponse createPresignedUploadUrl(
        Server server,
        String uploadType,
        String fileName,
        String contentType,
        long fileSize,
        String entityId
    ) {
        if (s3Presigner == null) {
            throw new IllegalStateException("S3 storage is not configured");
        }

        String key = StorageKeyUtils.buildKey(server, uploadType, fileName, entityId);

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(s3Configuration.getBucketName())
            .key(key)
            .contentType(contentType)
            .contentLength(fileSize)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_UPLOAD_DURATION)
            .putObjectRequest(putRequest)
            .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        Instant expiresAt = Instant.now().plus(PRESIGN_UPLOAD_DURATION);

        Map<String, String> requiredHeaders = new HashMap<>();
        requiredHeaders.put("Content-Type", contentType);

        return new PresignUploadResponse(
            presignedRequest.url().toString(),
            key,
            expiresAt,
            presignedRequest.httpRequest().method().name(),
            requiredHeaders
        );
    }

    public boolean verifyUploadExists(String key) {
        if (s3Client == null) {
            return false;
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .key(key)
                .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error verifying upload for key: {}", key, e);
            return false;
        }
    }

    public UploadResponse getUploadDetails(String key) {
        if (s3Client == null) {
            return null;
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .key(key)
                .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);
            String url = getCdnUrl(key);
            String fileName = StorageKeyUtils.extractFileName(key);

            return new UploadResponse(
                key,
                url,
                fileName,
                response.contentLength(),
                response.contentType()
            );
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            log.error("Error getting upload details for key: {}", key, e);
            return null;
        }
    }

    public List<S3ObjectInfo> listAllObjects(Server server) {
        return listObjectInfosByPrefix(server.getDatabaseName() + "/");
    }

    public List<S3ObjectInfo> listObjectInfosByPrefix(String prefix) {
        if (s3Client == null) {
            return Collections.emptyList();
        }

        List<S3ObjectInfo> objects = new ArrayList<>();

        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(s3Configuration.getBucketName())
            .prefix(prefix)
            .build();

        ListObjectsV2Response response;
        String continuationToken = null;

        do {
            if (continuationToken != null) {
                request = request.toBuilder().continuationToken(continuationToken).build();
            }

            response = s3Client.listObjectsV2(request);
            for (S3Object obj : response.contents()) {
                objects.add(new S3ObjectInfo(obj.key(), obj.size(), obj.lastModified()));
            }
            continuationToken = response.nextContinuationToken();
        } while (response.isTruncated());

        return objects;
    }

    public Map<String, Long> calculateStorageByType(Server server) {
        Map<String, Long> byType = new HashMap<>();
        byType.put("ticket", 0L);
        byType.put("evidence", 0L);
        byType.put("logs", 0L);
        byType.put("backup", 0L);
        byType.put("replay", 0L);
        byType.put("other", 0L);

        if (s3Client == null) {
            return byType;
        }

        String prefix = server.getDatabaseName() + "/";

        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(s3Configuration.getBucketName())
            .prefix(prefix)
            .build();

        ListObjectsV2Response response;
        String continuationToken = null;

        do {
            if (continuationToken != null) {
                request = request.toBuilder().continuationToken(continuationToken).build();
            }

            response = s3Client.listObjectsV2(request);
            for (S3Object obj : response.contents()) {
                String key = obj.key();
                String type = StorageKeyUtils.categorizeFile(key);
                byType.merge(type, obj.size(), Long::sum);
            }
            continuationToken = response.nextContinuationToken();
        } while (response.isTruncated());

        return byType;
    }

    private List<ObjectIdentifier> collectAllVersions(String key) {
        List<ObjectIdentifier> identifiers = new ArrayList<>();
        String bucket = s3Configuration.getBucketName();

        String keyMarker = null;
        String versionIdMarker = null;
        ListObjectVersionsResponse response;
        do {
            ListObjectVersionsRequest request = ListObjectVersionsRequest.builder()
                .bucket(bucket)
                .prefix(key)
                .keyMarker(keyMarker)
                .versionIdMarker(versionIdMarker)
                .build();

            response = s3Client.listObjectVersions(request);

            for (ObjectVersion version : response.versions()) {
                if (version.key().equals(key)) {
                    identifiers.add(ObjectIdentifier.builder()
                        .key(key)
                        .versionId(version.versionId())
                        .build());
                }
            }

            for (DeleteMarkerEntry marker : response.deleteMarkers()) {
                if (marker.key().equals(key)) {
                    identifiers.add(ObjectIdentifier.builder()
                        .key(key)
                        .versionId(marker.versionId())
                        .build());
                }
            }

            keyMarker = response.nextKeyMarker();
            versionIdMarker = response.nextVersionIdMarker();
        } while (Boolean.TRUE.equals(response.isTruncated()));

        return identifiers;
    }

    private String describeDeleteErrors(List<S3Error> errors) {
        return errors.stream()
            .map(error -> error.key() + ":" + error.code())
            .collect(Collectors.joining(", "));
    }

    public int bulkDelete(List<String> keys) {
        if (s3Client == null || keys == null || keys.isEmpty()) {
            return 0;
        }

        List<ObjectIdentifier> toDelete = new ArrayList<>();
        for (String key : keys) {
            toDelete.addAll(collectAllVersions(key));
        }

        if (toDelete.isEmpty()) {
            return 0;
        }

        int totalDeleted = 0;
        for (int i = 0; i < toDelete.size(); i += 1000) {
            List<ObjectIdentifier> batch = toDelete.subList(i, Math.min(i + 1000, toDelete.size()));
            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .delete(Delete.builder().objects(batch).quiet(true).build())
                .build();

            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            if (response.hasErrors()) {
                log.warn("Failed to delete one or more S3 object versions: {}", describeDeleteErrors(response.errors()));
            }
            totalDeleted += response.deleted().size();
        }

        return totalDeleted;
    }

    public UploadFileResult uploadFile(Server server, String uploadType, String fileName, String contentType, byte[] data) {
        if (s3Client == null) {
            throw new IllegalStateException("S3 storage is not configured");
        }

        String key = StorageKeyUtils.buildKey(server, uploadType, fileName, null);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Configuration.getBucketName())
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            return new UploadFileResult(key, getCdnUrl(key));
        } catch (Exception e) {
            log.error("Error uploading file: {}", key, e);
            throw new ExternalServiceException("Failed to upload file", e);
        }
    }
}
