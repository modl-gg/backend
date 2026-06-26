package gg.modl.backend.replay.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.replay.service.ReplayService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.SubmitReplayLabelsRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_REPLAYS)
@RequiredArgsConstructor
public class PanelReplayController {
    private final ReplayService replayService;
    private final ReplayProtoMapper mapper;

    @PostMapping("/{replayId}/label")
    public ResponseEntity<?> submitLabels(
        @PathVariable String replayId,
        @RequestBody SubmitReplayLabelsRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);

        ReplayService.SubmitLabelsResult result =
            replayService.submitLabels(server, replayId, mapper.toReplayLabels(request));
        if (result == ReplayService.SubmitLabelsResult.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "message", "Replay not found"
            ));
        }

        return ResponseEntity.ok(mapper.labelResponse("ok"));
    }
}
