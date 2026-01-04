package gg.modl.backend.storage.dto.response;

import java.util.Date;

public record StorageFileResponse(
        String key,
        String name,
        long size,
        String contentType,
        Date lastModified,
        String url
) {
}
