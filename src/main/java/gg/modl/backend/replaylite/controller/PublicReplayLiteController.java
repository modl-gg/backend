package gg.modl.backend.replaylite.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.replaylite.dto.ReplayLiteLabelRequest;
import gg.modl.backend.replaylite.dto.ReplayLitePublicResponse;
import gg.modl.backend.replaylite.service.ReplayLiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_REPLAY_LITE_REPLAYS)
@RequiredArgsConstructor
public class PublicReplayLiteController {
    private final ReplayLiteService replayLiteService;

    @GetMapping("/{replayId}")
    public ResponseEntity<?> getReplay(@PathVariable String replayId) {
        return replayLiteService.getPublicReplay(replayId)
            .<ResponseEntity<?>>map(this::toResponse)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "message", "Replay not found"
            )));
    }

    @PostMapping("/{replayId}/label")
    public ResponseEntity<Map<String, Object>> submitLabels(
        @PathVariable String replayId,
        @RequestBody @Valid ReplayLiteLabelRequest request,
        HttpServletRequest httpRequest
    ) {
        replayLiteService.submitLabels(replayId, request.labels(), RequestUtil.getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/{replayId}/download")
    public ResponseEntity<?> downloadReplay(@PathVariable String replayId) {
        return replayLiteService.getPublicReplayDownload(replayId)
            .<ResponseEntity<?>>map(download -> ResponseEntity.ok()
                .headers(downloadHeaders(replayId, download))
                .body(download.bytes()))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "message", "Replay not found"
            )));
    }

    private HttpHeaders downloadHeaders(String replayId, ReplayLiteService.ReplayLiteDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        headers.setContentLength(download.bytes().length);
        headers.setCacheControl("private, no-store, max-age=0");
        headers.setPragma("no-cache");
        headers.setExpires(0);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + replayId + ".modlreplay\"");
        return headers;
    }

    private ResponseEntity<Map<String, Object>> toResponse(ReplayLitePublicResponse replay) {
        return ResponseEntity.ok(Map.of(
            "replayId", replay.replayId(),
            "mcVersion", replay.mcVersion(),
            "fileSize", replay.fileSize(),
            "timestamp", replay.timestamp(),
            "replayUrl", replay.replayUrl(),
            "status", replay.status(),
            "labeled", replay.labeled()
        ));
    }
}
