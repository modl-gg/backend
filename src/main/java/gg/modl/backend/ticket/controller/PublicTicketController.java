package gg.modl.backend.ticket.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.request.AddReplyRequest;
import gg.modl.backend.ticket.dto.request.CreateTicketRequest;
import gg.modl.backend.ticket.dto.request.SubmitTicketFormRequest;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_TICKETS)
@RequiredArgsConstructor
public class PublicTicketController {
    private final TicketService ticketService;
    private final TicketEmailVerificationService verificationService;

    @PostMapping
    public ResponseEntity<?> createTicket(
            @RequestBody @Valid CreateTicketRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            TicketResponse ticket = ticketService.createTicket(server, createRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "ticketId", ticket.id(),
                    "message", "Ticket created successfully",
                    "ticket", Map.of(
                            "id", ticket.id(),
                            "type", ticket.type(),
                            "subject", ticket.subject(),
                            "status", ticket.status(),
                            "created", ticket.date().toInstant().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to create ticket"));
        }
    }

    @PostMapping("/unfinished")
    public ResponseEntity<?> createUnfinishedTicket(
            @RequestBody @Valid CreateTicketRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            TicketResponse ticket = ticketService.createTicket(server, createRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "ticketId", ticket.id(),
                    "message", "Ticket created successfully (Unfinished)",
                    "ticket", Map.of(
                            "id", ticket.id(),
                            "type", ticket.type(),
                            "subject", ticket.subject(),
                            "status", ticket.status(),
                            "created", ticket.date().toInstant().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to create unfinished ticket"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTicket(
            @PathVariable String id,
            @RequestHeader(value = "X-Ticket-Token", required = false) String ticketToken,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();

        // Hidden tickets return 404 for public access
        if (ticket.isHidden()) {
            return ResponseEntity.notFound().build();
        }

        // Email auth check
        if (ticket.isEmailAuthEnabled()) {
            if (ticketToken == null || !verificationService.validateToken(server, id, ticketToken)) {
                String emailHint = getEmailHint(ticket);
                Map<String, Object> body = new HashMap<>();
                body.put("requiresVerification", true);
                body.put("emailHint", emailHint != null ? emailHint : "");
                body.put("ticketId", id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }
        }

        return ticketService.getTicketById(server, id)
                .map(ticketResponse -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", ticketResponse.id());
                    response.put("_id", ticketResponse.id());
                    response.put("type", ticketResponse.type());
                    response.put("subject", ticketResponse.subject());
                    response.put("status", ticketResponse.status());
                    response.put("creator", ticketResponse.creator() != null ? ticketResponse.creator() : "");
                    response.put("creatorUuid", ticketResponse.creatorUuid() != null ? ticketResponse.creatorUuid() : "");
                    response.put("reportedBy", ticketResponse.reportedBy() != null ? ticketResponse.reportedBy() : "");
                    response.put("created", ticketResponse.date());
                    response.put("date", ticketResponse.date());
                    response.put("category", ticketResponse.category());
                    response.put("locked", ticketResponse.locked());
                    response.put("replies", ticketResponse.messages() != null ? ticketResponse.messages() : Collections.emptyList());
                    response.put("messages", ticketResponse.messages() != null ? ticketResponse.messages() : Collections.emptyList());
                    response.put("notes", ticketResponse.notes() != null ? ticketResponse.notes() : Collections.emptyList());
                    response.put("tags", ticketResponse.tags() != null ? ticketResponse.tags() : Collections.emptyList());
                    response.put("data", ticketResponse.data() != null ? ticketResponse.data() : Map.of());
                    response.put("formData", ticketResponse.formData() != null ? ticketResponse.formData() : Map.of());
                    response.put("reportedPlayer", ticketResponse.reportedPlayer() != null ? ticketResponse.reportedPlayer() : "");
                    response.put("reportedPlayerUuid", ticketResponse.reportedPlayerUuid() != null ? ticketResponse.reportedPlayerUuid() : "");
                    response.put("chatMessages", ticketResponse.chatMessages() != null ? ticketResponse.chatMessages() : Collections.emptyList());
                    response.put("emailAuthEnabled", ticketResponse.emailAuthEnabled());
                    return ResponseEntity.ok(response);
                })
                .<ResponseEntity<?>>map(r -> r)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getTicketStatus(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        // Check if ticket is hidden
        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        return ticketService.getTicketById(server, id)
                .map(ticket -> ResponseEntity.ok(Map.of(
                        "id", ticket.id(),
                        "type", ticket.type(),
                        "subject", ticket.subject(),
                        "status", ticket.status(),
                        "created", ticket.date(),
                        "locked", ticket.locked()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
            @PathVariable String id,
            @RequestBody @Valid AddReplyRequest replyRequest,
            @RequestHeader(value = "X-Ticket-Token", required = false) String ticketToken,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        // Check if ticket is hidden or requires email auth
        Optional<Ticket> rawTicket = ticketService.getTicketRaw(server, id);
        if (rawTicket.isEmpty() || rawTicket.get().isHidden()) {
            return ResponseEntity.notFound().build();
        }

        Ticket ticket = rawTicket.get();
        if (ticket.isEmailAuthEnabled()) {
            if (ticketToken == null || !verificationService.validateToken(server, id, ticketToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Forbidden", "message", "Email verification required"));
            }
        }

        try {
            Optional<TicketReply> replyOpt = ticketService.addReply(server, id, replyRequest);

            if (replyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Reply added successfully",
                    "reply", replyOpt.get()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Forbidden", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitTicketForm(
            @PathVariable String id,
            @RequestBody SubmitTicketFormRequest submitRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return ticketService.submitTicketForm(server, id, submitRequest)
                .map(ticket -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Ticket submitted successfully",
                        "ticket", Map.of(
                                "id", ticket.id(),
                                "subject", ticket.subject(),
                                "status", ticket.status()
                        )
                )))
                .orElse(ResponseEntity.notFound().build());
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
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bad Request", "message", "Email auth is not enabled for this ticket"));
        }

        try {
            String emailHint = verificationService.sendVerificationCode(server, ticket);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification code sent",
                    "emailHint", emailHint
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bad Request", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to send verification code"));
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyCode(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Bad Request", "message", "Code is required"));
        }

        String token = verificationService.verifyCode(server, id, code);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Forbidden", "message", "Invalid or expired code"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "message", "Verification successful"
        ));
    }

    private String getEmailHint(Ticket ticket) {
        if (ticket.getData() == null) return null;
        Object email = ticket.getData().get("creatorEmail");
        if (email == null) return null;
        String emailStr = email.toString();
        int atIndex = emailStr.indexOf('@');
        if (atIndex <= 1) return emailStr;
        return emailStr.charAt(0) + "***" + emailStr.substring(atIndex);
    }
}
