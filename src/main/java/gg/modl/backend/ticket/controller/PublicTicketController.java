package gg.modl.backend.ticket.controller;

import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketReplyService;
import gg.modl.backend.ticket.service.TicketService;
import gg.modl.proto.modl.v1.AddReplyRequest;
import gg.modl.proto.modl.v1.AddTicketReplyResponse;
import gg.modl.proto.modl.v1.PanelResource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
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
    private final TicketEmailVerificationService verificationService;
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

        TicketResponse ticket = ticketService.createTicket(server, PanelTicketProtoMapper.fromCreateTicketRequest(createRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, ticket.id());
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

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();

        if (ticket.isHidden()) {
            return ResponseEntity.notFound().build();
        }

        if (ticket.isEmailAuthEnabled()
            && (ticketToken == null || !verificationService.validateToken(server, id, ticketToken))) {
            String emailHint = ticketService.getEmailHint(ticket);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PublicTicketProtoMapper.toVerificationRequiredResponse(id, emailHint));
        }

        TicketResponse ticketResponse = ticketService.getTicketById(server, id);
        return ResponseEntity.ok(PublicTicketProtoMapper.toPublicTicketResponse(ticketResponse, ticket));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getTicketStatus(
        @PathVariable String id,
        @RequestParam(value = "token", required = false) String ticketToken,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();
        if (ticket.isEmailAuthEnabled()
            && (ticketToken == null || !verificationService.validateToken(server, id, ticketToken))) {
            throw new ForbiddenException("Email verification required");
        }

        TicketResponse ticketResp = ticketService.getTicketById(server, id);
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

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();
        if (ticket.isEmailAuthEnabled()
            && (ticketToken == null || !verificationService.validateToken(server, id, ticketToken))) {
            throw new ForbiddenException("Email verification required");
        }

        if (replyRequest.getStaff()) {
            throw new ValidationException("Public replies cannot be marked as staff");
        }

        TicketReply reply = ticketReplyService.addReply(server, id, PanelTicketProtoMapper.fromAddReplyRequest(replyRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, id);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AddTicketReplyResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Reply added successfully")
                .setReply(PanelTicketProtoMapper.toPublicTicketReply(reply))
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

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();
        if (ticket.isEmailAuthEnabled() && ticketService.getEmailHint(ticket) != null
            && (ticketToken == null || !verificationService.validateToken(server, id, ticketToken))) {
            throw new ForbiddenException("Email verification required");
        }

        TicketResponse ticketResp = ticketService.submitTicketForm(
            server, id, PublicTicketProtoMapper.fromSubmitTicketFormRequest(submitRequest));
        realtimeEventPublisher.invalidatePanel(server, PanelResource.PANEL_RESOURCE_TICKETS, id);
        return ResponseEntity.ok(PublicTicketProtoMapper.toSubmitResponse(ticketResp));
    }

    @PostMapping("/{id}/request-verification")
    public ResponseEntity<?> requestVerification(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();
        if (!ticket.isEmailAuthEnabled()) {
            throw new ValidationException("Email auth is not enabled for this ticket");
        }

        String emailHint = verificationService.sendVerificationCode(server, ticket);
        return ResponseEntity.ok(PublicTicketProtoMapper.toRequestVerificationResponse(emailHint));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyCode(
        @PathVariable String id,
        @RequestBody gg.modl.proto.modl.v1.VerifyTicketCodeRequest body,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        String token = verificationService.verifyCode(server, id, body.getCode());
        if (token == null) {
            throw new ForbiddenException("Invalid or expired code");
        }

        return ResponseEntity.ok(PublicTicketProtoMapper.toVerifyResponse(token));
    }
}
