package gg.modl.backend.appeal.controller;

import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.controller.PublicVerificationProtoMapper;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.PublicRecordAccessService;
import gg.modl.backend.ticket.service.PublicRecordAccessService.Access;
import gg.modl.backend.ticket.service.PublicRecordAccessService.AccessResult;
import gg.modl.backend.ticket.service.PublicRecordVerificationService;
import gg.modl.proto.modl.v1.CreatePublicAppealResponse;
import gg.modl.proto.modl.v1.PanelResource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_APPEALS)
@RequiredArgsConstructor
public class PublicAppealController {
    private final AppealService appealService;
    private final PublicRecordAccessService recordAccessService;
    private final PublicRecordVerificationService recordVerificationService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @GetMapping("/{id}")
    public ResponseEntity<?> getAppeal(
        @PathVariable String id,
        @RequestParam(value = "token", required = false) String appealToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket appeal = appealService.getAppealRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorize(server, appeal, appealToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PublicVerificationProtoMapper.toVerificationRequiredResponse(id, access.emailHint()));
        }

        TicketResponse appealResponse = appealService.toResponse(appeal);
        return ResponseEntity.ok(PublicAppealProtoMapper.toPublicAppealResponse(appealResponse));
    }

    @PostMapping
    public ResponseEntity<CreatePublicAppealResponse> createAppeal(
        @RequestBody gg.modl.proto.modl.v1.CreateAppealRequest createRequest,
        @RequestParam(value = "token", required = false) String appealToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        TicketResponse appeal = appealService.createAppeal(server, PanelAppealProtoMapper.fromCreateAppealRequest(createRequest), appealToken);
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, appeal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicAppealProtoMapper.toCreateAppealResponse(appeal));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.AddAppealReplyRequest replyRequest,
        @RequestParam(value = "token", required = false) String appealToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket appeal = appealService.getAppealRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorize(server, appeal, appealToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PublicVerificationProtoMapper.toVerificationRequiredResponse(id, access.emailHint()));
        }

        List<Object> attachments = PanelAppealProtoMapper.valueListToObjects(replyRequest.getAttachmentsList());
        TicketReply reply = appealService.addPublicReply(server, id, replyRequest.getContent(), attachments);
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_APPEALS, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicAppealProtoMapper.toAddReplyResponse(reply));
    }

    @PostMapping("/{id}/request-verification")
    public ResponseEntity<?> requestVerification(@PathVariable String id, HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket appeal = appealService.getAppealRaw(server, id).filter(a -> !a.isHidden()).orElse(null);
        if (appeal == null) {
            return ResponseEntity.notFound().build();
        }

        String emailHint = recordVerificationService.sendVerificationCode(server, appeal);
        return ResponseEntity.ok(PublicVerificationProtoMapper.toRequestVerificationResponse(emailHint));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyCode(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.VerifyTicketCodeRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (appealService.getAppealRaw(server, id).filter(a -> !a.isHidden()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String token = recordVerificationService.verifyCode(server, id, body.getCode());
        return ResponseEntity.ok(PublicVerificationProtoMapper.toVerifyResponse(token));
    }
}
