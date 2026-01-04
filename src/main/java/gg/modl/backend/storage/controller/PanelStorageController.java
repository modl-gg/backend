package gg.modl.backend.storage.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.storage.dto.response.StorageFileResponse;
import gg.modl.backend.storage.dto.response.StorageQuotaResponse;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<List<StorageFileResponse>> getFiles(
            @RequestParam(required = false) String prefix,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<StorageFileResponse> files = s3StorageService.listFiles(server, prefix);
        return ResponseEntity.ok(files);
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(
            @RequestBody Map<String, List<String>> body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<String> keys = body.get("keys");

        if (keys == null || keys.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No keys provided"));
        }

        String prefix = server.getDatabaseName() + "/";
        for (String key : keys) {
            if (!key.startsWith(prefix)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied for key: " + key));
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
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        String url = s3StorageService.getPresignedUrl(key);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("url", url));
    }
}
