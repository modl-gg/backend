package gg.modl.backend.storage.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.longValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.toTimestamp;

import gg.modl.backend.storage.dto.response.PresignUploadResponse;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import gg.modl.backend.storage.dto.response.UploadResponse;
import gg.modl.proto.modl.v1.AiQuotaInfo;
import gg.modl.proto.modl.v1.MediaConfigResponse;
import gg.modl.proto.modl.v1.MediaSupportedTypes;
import gg.modl.proto.modl.v1.StorageBulkDeleteResponse;
import gg.modl.proto.modl.v1.StorageDownloadUrlResponse;
import gg.modl.proto.modl.v1.StorageFilesResponse;
import gg.modl.proto.modl.v1.StorageSyncResponse;
import java.util.List;
import java.util.Map;

final class StorageProtoMapper {

    private StorageProtoMapper() {
    }

    static StorageFilesResponse toStorageFilesResponse(List<StorageFileResponse> files) {
        StorageFilesResponse.Builder builder = StorageFilesResponse.newBuilder();
        if (files != null) {
            files.forEach(file -> builder.addFiles(toStorageFileResponse(file)));
        }
        return builder.build();
    }

    static StorageBulkDeleteResponse toStorageBulkDeleteResponse(int deleted) {
        return StorageBulkDeleteResponse.newBuilder().setDeleted(deleted).build();
    }

    static StorageSyncResponse toStorageSyncResponse(int synced) {
        return StorageSyncResponse.newBuilder().setSynced(synced).build();
    }

    static StorageDownloadUrlResponse toStorageDownloadUrlResponse(String url) {
        return StorageDownloadUrlResponse.newBuilder().setUrl(stringValue(url)).build();
    }

    static gg.modl.proto.modl.v1.StorageQuotaResponse toStorageQuotaResponse(StorageQuotaResponse model) {
        gg.modl.proto.modl.v1.StorageQuotaResponse.Builder builder = gg.modl.proto.modl.v1.StorageQuotaResponse.newBuilder()
            .setUsedBytes(model.usedBytes())
            .setMaxBytes(model.maxBytes())
            .setUsedPercentage(model.usedPercentage())
            .setUsedFormatted(stringValue(model.usedFormatted()))
            .setMaxFormatted(stringValue(model.maxFormatted()))
            .setIsPremium(model.isPremium())
            .setStorageOverageRate(model.storageOverageRate());
        if (model.byType() != null) {
            model.byType().forEach((type, bytes) -> builder.putByType(type, longValue(bytes)));
        }
        if (model.aiQuota() != null) {
            builder.setAiQuota(toAiQuotaInfo(model.aiQuota()));
        }
        return builder.build();
    }

    static MediaConfigResponse toMediaConfigResponse(boolean backblazeConfigured,
                                                     Map<String, Object> supportedTypes,
                                                     Map<String, Object> fileSizeLimits,
                                                     String cdnDomain) {
        MediaConfigResponse.Builder builder = MediaConfigResponse.newBuilder()
            .setBackblazeConfigured(backblazeConfigured);
        if (supportedTypes != null) {
            supportedTypes.forEach((key, value) -> builder.putSupportedTypes(key, toMediaSupportedTypes(value)));
        }
        if (fileSizeLimits != null) {
            fileSizeLimits.forEach((key, value) -> builder.putFileSizeLimits(key, longValue(value)));
        }
        if (cdnDomain != null && !cdnDomain.isBlank()) {
            builder.setCdnDomain(cdnDomain);
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.PresignUploadResponse toPresignUploadResponse(PresignUploadResponse model) {
        gg.modl.proto.modl.v1.PresignUploadResponse.Builder builder = gg.modl.proto.modl.v1.PresignUploadResponse.newBuilder()
            .setPresignedUrl(stringValue(model.presignedUrl()))
            .setKey(stringValue(model.key()))
            .setMethod(stringValue(model.method()));
        if (model.expiresAt() != null) {
            builder.setExpiresAt(toTimestamp(model.expiresAt()));
        }
        if (model.requiredHeaders() != null) {
            builder.putAllRequiredHeaders(model.requiredHeaders());
        }
        return builder.build();
    }

    static gg.modl.proto.modl.v1.UploadResponse toUploadResponse(UploadResponse model) {
        return gg.modl.proto.modl.v1.UploadResponse.newBuilder()
            .setKey(stringValue(model.key()))
            .setUrl(stringValue(model.url()))
            .setFileName(stringValue(model.fileName()))
            .setSize(model.size())
            .setContentType(stringValue(model.contentType()))
            .build();
    }

    private static gg.modl.proto.modl.v1.StorageFileResponse toStorageFileResponse(StorageFileResponse model) {
        gg.modl.proto.modl.v1.StorageFileResponse.Builder builder = gg.modl.proto.modl.v1.StorageFileResponse.newBuilder()
            .setKey(stringValue(model.key()))
            .setName(stringValue(model.name()))
            .setSize(model.size())
            .setContentType(stringValue(model.contentType()))
            .setUrl(stringValue(model.url()));
        if (model.lastModified() != null) {
            builder.setLastModified(toTimestamp(model.lastModified()));
        }
        return builder.build();
    }

    private static AiQuotaInfo toAiQuotaInfo(StorageQuotaResponse.AiQuotaInfo model) {
        AiQuotaInfo.Builder builder = AiQuotaInfo.newBuilder()
            .setTotalUsed(model.totalUsed())
            .setBaseLimit(model.baseLimit())
            .setOverageUsed(model.overageUsed())
            .setOverageCost(model.overageCost())
            .setCanUseAi(model.canUseAI())
            .setUsagePercentage(model.usagePercentage());
        if (model.byService() != null) {
            model.byService().forEach((service, count) -> builder.putByService(service, longValue(count)));
        }
        return builder.build();
    }

    private static MediaSupportedTypes toMediaSupportedTypes(Object value) {
        MediaSupportedTypes.Builder builder = MediaSupportedTypes.newBuilder();
        if (value instanceof List<?> types) {
            types.forEach(type -> builder.addTypes(stringValue(type)));
        }
        return builder.build();
    }
}
