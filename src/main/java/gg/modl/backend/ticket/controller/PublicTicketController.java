package gg.modl.backend.ticket.controller;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.PublicRecordAccessService;
import gg.modl.backend.ticket.service.PublicRecordAccessService.Access;
import gg.modl.backend.ticket.service.PublicRecordAccessService.AccessResult;
import gg.modl.backend.ticket.service.PublicRecordVerificationService;
import gg.modl.backend.ticket.service.TicketReplyService;
import gg.modl.backend.ticket.service.TicketService;
import gg.modl.proto.modl.v1.AddReplyRequest;
import gg.modl.proto.modl.v1.AddTicketReplyResponse;
import gg.modl.proto.modl.v1.PanelResource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
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
@RequestMapping(RESTMappingV1.PUBLIC_TICKETS)
@RequiredArgsConstructor
public class PublicTicketController {
    private final TicketService ticketService;
    private final TicketReplyService ticketReplyService;
    private final PublicRecordAccessService recordAccessService;
    private final PublicRecordVerificationService recordVerificationService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @PostMapping
    public ResponseEntity<gg.modl.proto.modl.v1.CreateTicketResponse> createTicket(
        @RequestBody gg.modl.proto.modl.v1.CreateTicketRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        TicketResponse ticket = ticketService.createTicket(server, PanelTicketProtoMapper.fromCreateTicketRequest(createRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, ticket.id());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PublicTicketProtoMapper.toCreateTicketResponse(ticket, "Ticket created successfully"));
    }

    @PostMapping("/unfinished")
    public ResponseEntity<gg.modl.proto.modl.v1.CreateTicketResponse> createUnfinishedTicket(
        @RequestBody gg.modl.proto.modl.v1.CreateTicketRequest createRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        TicketResponse ticket = ticketService.createUnfinishedTicket(server, PanelTicketProtoMapper.fromCreateTicketRequest(createRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PublicTicketProtoMapper.toCreateTicketResponse(ticket, "Ticket created successfully (Unfinished)"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTicket(
        @PathVariable String id,
        @RequestParam(value = "token", required = false) String ticketToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket ticket = ticketService.getTicketRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorize(server, ticket, ticketToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PublicVerificationProtoMapper.toVerificationRequiredResponse(id, access.emailHint()));
        }

        TicketResponse ticketResponse = ticketService.toResponse(server, ticket);
        Set<String> formFieldAllowlist = ticketService.getPublicFormFieldIds(server, ticket);
        return ResponseEntity.ok(PublicTicketProtoMapper.toPublicTicketResponse(ticketResponse, ticket, formFieldAllowlist));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getTicketStatus(
        @PathVariable String id,
        @RequestParam(value = "token", required = false) String ticketToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket ticket = ticketService.getTicketRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorize(server, ticket, ticketToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            throw new ForbiddenException("Email verification required");
        }

        TicketResponse ticketResp = ticketService.toResponse(server, ticket);
        return ResponseEntity.ok(PublicTicketProtoMapper.toStatusResponse(ticketResp));
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
        @PathVariable String id,
        @RequestBody AddReplyRequest replyRequest,
        @RequestParam(value = "token", required = false) String ticketToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket ticket = ticketService.getTicketRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorize(server, ticket, ticketToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            throw new ForbiddenException("Email verification required");
        }

        List<Object> attachments = PublicTicketProtoMapper.attachmentsFromReply(replyRequest);
        TicketReply reply = ticketReplyService.addPublicReply(server, id, replyRequest.getContent(), attachments);
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, id);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AddTicketReplyResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Reply added successfully")
                .setReply(PublicTicketProtoMapper.toPublicReply(reply))
                .build());
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitTicketForm(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.SubmitTicketFormRequest submitRequest,
        @RequestParam(value = "token", required = false) String ticketToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket ticket = ticketService.getTicketRaw(server, id).orElse(null);
        AccessResult access = recordAccessService.authorizeSubmission(server, ticket, ticketToken);
        if (access.access() == Access.NOT_FOUND) {
            return ResponseEntity.notFound().build();
        }
        if (access.access() == Access.TOKEN_REQUIRED) {
            throw new ForbiddenException("Email verification required");
        }

        if (ticket.isLocked() || (ticket.getStatus() != null && ticket.getStatus().isTerminal())) {
            throw new ForbiddenException("Ticket is closed and cannot be resubmitted");
        }

        TicketResponse ticketResp = ticketService.submitTicketForm(
            server, id, PublicTicketProtoMapper.fromSubmitTicketFormRequest(submitRequest), access.tokenVerified());
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, id);
        return ResponseEntity.ok(PublicTicketProtoMapper.toSubmitResponse(ticketResp));
    }

    @PostMapping("/{id}/request-verification")
    public ResponseEntity<?> requestVerification(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Ticket ticket = ticketService.getTicketRaw(server, id).filter(t -> !t.isHidden()).orElse(null);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }

        String emailHint = recordVerificationService.sendVerificationCode(server, ticket);
        return ResponseEntity.ok(PublicVerificationProtoMapper.toRequestVerificationResponse(emailHint));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyCode(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.VerifyTicketCodeRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        if (ticketService.getTicketRaw(server, id).filter(t -> !t.isHidden()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String token = recordVerificationService.verifyCode(server, id, body.getCode());
        return ResponseEntity.ok(PublicVerificationProtoMapper.toVerifyResponse(token));
    }
}
