package gg.modl.backend.settings.controller;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DomainSettings;
import gg.modl.backend.settings.service.CustomDomainAccessService;
import gg.modl.backend.settings.service.DomainSettingsService;
import gg.modl.proto.modl.v1.ConfigureDomainRequest;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.RemoveDomainResponse;
import gg.modl.proto.modl.v1.VerifyDomainRequest;
import gg.modl.proto.modl.v1.VerifyDomainResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS + "/domain")
@RequiredArgsConstructor
public class PanelDomainSettingsController {
    private final DomainSettingsService domainSettingsService;
    private final CustomDomainAccessService customDomainAccessService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping
    public gg.modl.proto.modl.v1.DomainSettings getDomainSettings(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        String host = request.getHeader("Host");
        DomainSettings settings = domainSettingsService.getDomainSettings(server, host);
        return PanelSettingsProtoMapper.toDomainSettings(settings);
    }

    @PostMapping
    public gg.modl.proto.modl.v1.DomainSettings configureDomain(
        @RequestBody ConfigureDomainRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireCustomDomainWriteAccess(server);

        DomainSettings settings = domainSettingsService.configureDomain(server, body.getCustomDomain().trim());
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toDomainSettings(settings);
    }

    private void requireCustomDomainWriteAccess(Server server) {
        if (!customDomainAccessService.canManageCustomDomain(server)) {
            throw new ForbiddenException("Custom domains require Premium unless your server is grandfathered.");
        }
    }

    @PostMapping("/verify")
    public VerifyDomainResponse verifyDomain(
        @RequestBody VerifyDomainRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        requireCustomDomainWriteAccess(server);

        DomainSettings settings = domainSettingsService.verifyDomain(server, body.getDomain().trim());
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

        invalidateSettings(server);
        return PanelSettingsProtoMapper.toVerifyDomainResponse(settings, message);
    }

    @DeleteMapping
    public RemoveDomainResponse removeDomain(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        requireCustomDomainWriteAccess(server);

        domainSettingsService.removeDomain(server);
        invalidateSettings(server);
        return PanelSettingsProtoMapper.toRemoveDomainResponse("Domain removed successfully");
    }

    private void invalidateSettings(Server server) {
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_SETTINGS);
    }
}
