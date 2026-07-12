package gg.modl.backend.player.controller;

import gg.modl.backend.player.dto.request.StartupRequest;
import gg.modl.backend.player.service.MinecraftStartupService;
import gg.modl.backend.infrastructure.rest.RESTMappingV2;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV2.PREFIX_MINECRAFT)
@RequiredArgsConstructor
public class MinecraftStartupController {
    private final MinecraftStartupService minecraftStartupService;

    @PostMapping("/startup")
    public ResponseEntity<Map<String, Object>> startup(
        @RequestBody @Valid StartupRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String clientIp = RequestUtil.getClientIp(httpRequest);
        return ResponseEntity.ok(minecraftStartupService.handleStartup(server, request, clientIp));
    }

}
