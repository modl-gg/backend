package gg.modl.backend.server.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.ProvisioningStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SERVER)
@RequiredArgsConstructor
public class PanelServerController {
    private final Environment environment;

    @GetMapping("/test")
    public String getTest() {
        return environment.getProperty("spring.application.name");
    }

    @GetMapping("/provisioning-status")
    public ResponseEntity<ProvisioningStatusResponse> getProvisioningStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelServerProtoMapper.toProvisioningStatusResponse(server));
    }
}
