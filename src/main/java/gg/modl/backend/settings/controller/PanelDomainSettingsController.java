package gg.modl.backend.settings.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.dto.request.ConfigureDomainRequest;
import gg.modl.backend.settings.dto.request.VerifyDomainRequest;
import gg.modl.backend.settings.service.CustomDomainAccessService;
import gg.modl.backend.settings.service.DomainSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS + "/domain")
@RequiredArgsConstructor
public class PanelDomainSettingsController {
    private final DomainSettingsService domainSettingsService;
    private final CustomDomainAccessService customDomainAccessService;

    @GetMapping
    public ResponseEntity<DomainSettings> getDomainSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String host = request.getHeader("Host");
        DomainSettings settings = domainSettingsService.getDomainSettings(server, host);
        return ResponseEntity.ok(settings);
    }

    @PostMapping
    public ResponseEntity<?> configureDomain(
            @RequestBody @Valid ConfigureDomainRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            DomainSettings settings = domainSettingsService.configureDomain(server, body.customDomain().trim());
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyDomain(
            @RequestBody @Valid VerifyDomainRequest body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            DomainSettings settings = domainSettingsService.verifyDomain(server, body.domain().trim());
            DomainSettings.DomainStatus status = settings.getStatus();

            String message = switch (status.getStatus()) {
                case "active" -> status.getSslStatus().equals("active")
                        ? "Domain verified successfully with active SSL!"
                        : "Domain verified! SSL certificate is being provisioned.";
                case "error" -> status.getError() != null
                        ? status.getError()
                        : "Domain verification failed";
                default -> "Domain verification pending. Please ensure your CNAME is configured correctly.";
            };

            return ResponseEntity.ok(Map.of(
                    "status", status,
                    "message", message
            ));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> removeDomain(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        ResponseEntity<?> denied = requireCustomDomainWriteAccess(server);
        if (denied != null) {
            return denied;
        }

        try {
            domainSettingsService.removeDomain(server);
            return ResponseEntity.ok(Map.of("message", "Domain removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> requireCustomDomainWriteAccess(Server server) {
        if (customDomainAccessService.canManageCustomDomain(server)) {
            return null;
        }

        return ResponseEntity.status(403).body(Map.of(
                "message", "Custom domains require Premium unless your server is grandfathered."
        ));
    }
}
