package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class S3StorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3StorageService(
            @org.springframework.lang.Nullable S3Client s3Client,
            @org.springframework.lang.Nullable S3Presigner s3Presigner
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        if (s3Client == null) {
            log.warn("S3 storage is not configured. File storage features will be disabled.");
        }
    }

    @Value("${modl.storage.bucket-name:}")
    private String bucketName;

    @Value("${modl.storage.cdn-domain:}")
    private String cdnDomain;

    public boolean isConfigured() {
        return s3Client != null && bucketName != null && !bucketName.isBlank();
    }

    public String getCdnDomain() {
        return cdnDomain;
    }

    public String getCdnUrl(String key) {
        if (cdnDomain == null || cdnDomain.isBlank()) {
            return getPresignedUrl(key);
        }
        return String.format("https://%s/%s", cdnDomain, key);
    }

    public boolean deleteFile(String key) {
        if (s3Client == null) {
            return false;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
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
                .bucket(bucketName)
                .prefix(fullPrefix)
                .maxKeys(1000)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents().stream()
                .map(obj -> new StorageFileResponse(
                        obj.key(),
                        extractFileName(obj.key()),
                        obj.size(),
                        "application/octet-stream",
                        Date.from(obj.lastModified()),
                        getCdnUrl(obj.key())
                ))
                .toList();
    }

    public String getPresignedUrl(String key) {
        if (s3Presigner == null) {
            return null;
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private static final Duration PRESIGN_UPLOAD_DURATION = Duration.ofMinutes(15);

    public PresignUploadResponse createPresignedUploadUrl(
            Server server,
            String uploadType,
            String fileName,
            String contentType,
            long fileSize
    ) {
        if (s3Presigner == null) {
            throw new IllegalStateException("S3 storage is not configured");
        }

        String key = buildKey(server, uploadType, fileName);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
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
                    .bucket(bucketName)
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
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);
            String url = getCdnUrl(key);
            String fileName = extractFileName(key);

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

    public long calculateStorageUsed(Server server) {
        if (s3Client == null) {
            return 0;
        }

        String prefix = server.getDatabaseName() + "/";

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        long totalSize = 0;
        ListObjectsV2Response response;
        String continuationToken = null;

        do {
            if (continuationToken != null) {
                request = request.toBuilder().continuationToken(continuationToken).build();
            }

            response = s3Client.listObjectsV2(request);
            totalSize += response.contents().stream().mapToLong(S3Object::size).sum();
            continuationToken = response.nextContinuationToken();
        } while (response.isTruncated());

        return totalSize;
    }

    public int bulkDelete(List<String> keys) {
        if (s3Client == null || keys.isEmpty()) {
            return 0;
        }

        List<ObjectIdentifier> toDelete = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(toDelete).build())
                .build();

        DeleteObjectsResponse response = s3Client.deleteObjects(request);
        return response.deleted().size();
    }

    private String buildKey(Server server, String uploadType, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";
        return String.format("%s/%s/%s%s", server.getDatabaseName(), uploadType, uuid, extension);
    }

    private String extractFileName(String key) {
        return key.substring(key.lastIndexOf("/") + 1);
    }
}
