package gg.modl.backend.storage.service;

import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${modl.storage.bucket-name:}")
    private String bucketName;

    public boolean isConfigured() {
        return s3Client != null && bucketName != null && !bucketName.isBlank();
    }

    public UploadResponse uploadFile(Server server, MultipartFile file, String uploadType) throws IOException {
        if (s3Client == null) {
            throw new IllegalStateException("S3 storage is not configured");
        }

        String fileName = file.getOriginalFilename();
        String key = buildKey(server, uploadType, fileName);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String url = getPresignedUrl(key);

        return new UploadResponse(key, url, fileName, file.getSize(), file.getContentType());
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
                        getPresignedUrl(obj.key())
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
