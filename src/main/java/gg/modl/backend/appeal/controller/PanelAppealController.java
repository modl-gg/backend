package gg.modl.backend.appeal.controller;

import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddTicketReplyResponse;
import gg.modl.proto.modl.v1.AppealTicketsResponse;
import gg.modl.proto.modl.v1.PanelResource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_APPEALS)
@RequiredArgsConstructor
public class PanelAppealController {
    private final AppealService appealService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping("/punishment/{punishmentId}")
    public ResponseEntity<AppealTicketsResponse> getAppealsByPunishment(
        @PathVariable String punishmentId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<TicketResponse> appeals = appealService.getAppealsByPunishment(server, punishmentId);

        if (appeals.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PanelAppealProtoMapper.toAppealTicketsResponse(appeals));
    }

    @GetMapping("/{id}")
    public ResponseEntity<gg.modl.proto.modl.v1.TicketResponse> getAppealById(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return ResponseEntity.ok(PanelAppealProtoMapper.toTicketResponse(appealService.getAppealById(server, id)));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<AddTicketReplyResponse> addReply(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.AddAppealReplyRequest replyRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketReply reply = appealService.addReply(server, id, PanelAppealProtoMapper.fromAddAppealReplyRequest(replyRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(PanelAppealProtoMapper.toAddReplyResponse(reply));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<gg.modl.proto.modl.v1.TicketResponse> updateStatus(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.UpdateAppealStatusRequest statusRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        TicketResponse appeal = appealService.updateStatus(server, id, PanelAppealProtoMapper.fromUpdateAppealStatusRequest(statusRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, id);
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_PUNISHMENTS);
        return ResponseEntity.ok(PanelAppealProtoMapper.toTicketResponse(appeal));
    }
}
