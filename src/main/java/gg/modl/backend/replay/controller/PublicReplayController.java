package gg.modl.backend.replay.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.replay.service.ReplayService;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_REPLAYS)
@RequiredArgsConstructor
public class PublicReplayController {
    private final ReplayService replayService;
    private final ReplayProtoMapper mapper;

    @GetMapping("/{replayId}")
    public ResponseEntity<?> getReplay(
        @PathVariable String replayId,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);

        return replayService.getPublicReplay(server, replayId)
            .<ResponseEntity<?>>map(replay -> ResponseEntity.ok(mapper.toReplayResponse(replay)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404,
                "message", "Replay not found"
            )));
    }
}
