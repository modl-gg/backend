package gg.modl.backend.settings.controller;

import gg.modl.backend.ai.service.AITicketAnalysisService;
import gg.modl.backend.infrastructure.authorization.PanelAccessRule;
import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.server.data.Server;
import gg.modl.proto.modl.v1.AISuggestionActionResponse;
import gg.modl.proto.modl.v1.ApplyAIPunishmentRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_SETTINGS)
@RequiresPanelPermission(view = PermissionService.ADMIN_SETTINGS_VIEW_PUNISHMENTS, modify = PermissionService.ADMIN_SETTINGS_MODIFY_PUNISHMENTS)
@RequiredArgsConstructor
public class PanelAiSuggestionController {
    private final AITicketAnalysisService aiTicketAnalysisService;

    @PostMapping("/ai-apply-punishment/{ticketId}")
    @RequiresPanelPermission(rule = PanelAccessRule.PERMIT_ALL)
    public AISuggestionActionResponse applyAIPunishment(
        @PathVariable String ticketId,
        @RequestBody ApplyAIPunishmentRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        String email = RequestUtil.getSessionEmail(request);

        AITicketAnalysisService.AISuggestionResult result = aiTicketAnalysisService.applyAISuggestion(server, ticketId, email);
        return toAISuggestionResponse(result);
    }

    @PostMapping("/ai-dismiss-suggestion/{ticketId}")
    public AISuggestionActionResponse dismissAISuggestion(
        @PathVariable String ticketId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        AITicketAnalysisService.AISuggestionResult result = aiTicketAnalysisService.dismissAISuggestion(server, ticketId);
        return toAISuggestionResponse(result);
    }

    private AISuggestionActionResponse toAISuggestionResponse(AITicketAnalysisService.AISuggestionResult result) {
        return PanelSettingsProtoMapper.toAISuggestionActionResponse(result.success(), result.error(), null);
    }
}
