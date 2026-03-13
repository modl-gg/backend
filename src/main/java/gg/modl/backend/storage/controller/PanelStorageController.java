package gg.modl.backend.storage.controller;

import gg.modl.backend.exception.ForbiddenException;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.request.BulkDeleteRequest;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_STORAGE)
@RequiredArgsConstructor
public class PanelStorageController {
    private final S3StorageService s3StorageService;
    private final StorageQuotaService quotaService;

    @GetMapping("/quota")
    public ResponseEntity<StorageQuotaResponse> getQuota(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        StorageQuotaResponse quota = quotaService.getQuota(server);
        return ResponseEntity.ok(quota);
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFiles(
        @RequestParam(required = false) String prefix,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<StorageFileResponse> files = s3StorageService.listFiles(server, prefix);
        return ResponseEntity.ok(Map.of("files", files));
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(
        @RequestBody @Valid BulkDeleteRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<String> keys = body.keys();

        String prefix = server.getDatabaseName() + "/";
        for (String key : keys) {
            if (!key.startsWith(prefix)) {
                throw new ForbiddenException("Access denied for key: " + key);
            }
        }

        int deleted = s3StorageService.bulkDelete(keys);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/download/{*key}")
    public ResponseEntity<?> getDownloadUrl(
        @PathVariable String key,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (!key.startsWith(server.getDatabaseName() + "/")) {
            throw new ForbiddenException("Access denied");
        }

        String url = s3StorageService.getPresignedUrl(key);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("url", url));
    }
}
