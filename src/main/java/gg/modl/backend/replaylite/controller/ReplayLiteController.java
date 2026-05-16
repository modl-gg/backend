package gg.modl.backend.replaylite.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.replaylite.dto.ReplayLiteUploadInitRequest;
import gg.modl.backend.replaylite.dto.ReplayLiteUploadInitResponse;
import gg.modl.backend.replaylite.service.ReplayLiteService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.REPLAY_LITE_REPLAYS)
@RequiredArgsConstructor
public class ReplayLiteController {
    private final ReplayLiteService replayLiteService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> initUpload(
        @RequestBody @Valid ReplayLiteUploadInitRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        ReplayLiteUploadInitResponse response = replayLiteService.initUpload(
            server,
            request,
            RequestUtil.getClientIp(httpRequest)
        );

        return ResponseEntity.ok(Map.of(
            "status", 200,
            "replayId", response.replayId(),
            "uploadUrl", response.uploadUrl(),
            "method", response.method(),
            "requiredHeaders", response.requiredHeaders(),
            "expiresAt", response.expiresAt()
        ));
    }

    @PostMapping("/{replayId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(
        @PathVariable String replayId,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        replayLiteService.confirmUpload(server, replayId, RequestUtil.getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of(
            "status", 200,
            "success", true
        ));
    }
}
