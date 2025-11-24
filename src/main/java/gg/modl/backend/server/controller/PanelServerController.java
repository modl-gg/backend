package gg.modl.backend.server.controller;

import gg.modl.backend.rest.RESTMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMapping.V1_PANEL_PREFIX)
@RequiredArgsConstructor
public class PanelServerController {
    private final Environment environment;

    @GetMapping("/test")
    public String getTest() {
        return environment.getProperty("spring.application.name");
    }
}
