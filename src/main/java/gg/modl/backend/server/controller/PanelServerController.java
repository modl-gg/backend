package gg.modl.backend.server.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    public ResponseEntity<Map<String, Object>> getProvisioningStatus(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        return ResponseEntity.ok(Map.of(
                "status", server.getProvisioningStatus().name(),
                "serverName", server.getServerName(),
                "emailVerified", server.getEmailVerified() != null && server.getEmailVerified()
        ));
    }
}
