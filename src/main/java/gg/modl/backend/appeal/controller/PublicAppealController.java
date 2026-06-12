package gg.modl.backend.appeal.controller;

import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.proto.modl.v1.AddPublicAppealReplyResponse;
import gg.modl.proto.modl.v1.CreatePublicAppealResponse;
import gg.modl.proto.modl.v1.PanelResource;
import gg.modl.proto.modl.v1.PublicAppealResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_APPEALS)
@RequiredArgsConstructor
public class PublicAppealController {
    private final AppealService appealService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping("/{id}")
    public ResponseEntity<PublicAppealResponse> getAppeal(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        TicketResponse appeal = appealService.getAppealById(server, id);
        return ResponseEntity.ok(PublicAppealProtoMapper.toPublicAppealResponse(appeal));
    }

    @PostMapping
    public ResponseEntity<CreatePublicAppealResponse> createAppeal(
        @RequestBody gg.modl.proto.modl.v1.CreateAppealRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        TicketResponse appeal = appealService.createAppeal(server, PanelAppealProtoMapper.fromCreateAppealRequest(createRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, appeal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicAppealProtoMapper.toCreateAppealResponse(appeal));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<AddPublicAppealReplyResponse> addReply(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.AddAppealReplyRequest replyRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (replyRequest.getStaff()) {
            throw new ValidationException("Public replies cannot be marked as staff");
        }

        TicketReply reply = appealService.addReply(server, id, PanelAppealProtoMapper.fromAddAppealReplyRequest(replyRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicAppealProtoMapper.toAddReplyResponse(reply));
    }
}
