package gg.modl.backend.storage.dto.response;

public record UploadResponse(
        String key,
        String url,
        String fileName,
        long size,
        String contentType
) {
}
