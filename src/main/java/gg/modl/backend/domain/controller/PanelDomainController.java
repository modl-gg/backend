package gg.modl.backend.domain.controller;

import gg.modl.backend.domain.dto.request.AddDomainRequest;
import gg.modl.backend.domain.dto.response.DomainInstructionsResponse;
import gg.modl.backend.domain.dto.response.DomainStatusResponse;
import gg.modl.backend.domain.service.DomainService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_DOMAIN)
@RequiredArgsConstructor
public class PanelDomainController {
    private final DomainService domainService;

    @GetMapping
    public ResponseEntity<DomainStatusResponse> getDomainConfig(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        DomainStatusResponse status = domainService.getDomainConfig(server);
        return ResponseEntity.ok(status);
    }

    @PostMapping
    public ResponseEntity<?> addDomain(
            @RequestBody @Valid AddDomainRequest addRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            DomainStatusResponse status = domainService.addDomain(server, addRequest.domain());
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<DomainStatusResponse> verifyDomain(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String domain = server.getCustomDomain();

        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        DomainStatusResponse status = domainService.verifyDomain(server, domain);
        return ResponseEntity.ok(status);
    }

    @DeleteMapping
    public ResponseEntity<?> deleteDomain(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String domain = server.getCustomDomain();

        if (domain == null || domain.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        boolean deleted = domainService.deleteDomain(server, domain);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Domain removed"));
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to remove domain"));
    }

    @GetMapping("/status/{domain}")
    public ResponseEntity<DomainStatusResponse> getDomainStatus(
            @PathVariable String domain,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        DomainStatusResponse status = domainService.verifyDomain(server, domain);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/instructions")
    public ResponseEntity<DomainInstructionsResponse> getInstructions() {
        DomainInstructionsResponse instructions = domainService.getInstructions();
        return ResponseEntity.ok(instructions);
    }
}
