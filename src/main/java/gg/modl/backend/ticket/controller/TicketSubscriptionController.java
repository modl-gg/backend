package gg.modl.backend.ticket.controller;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.dto.response.SubscriptionUpdateResponse;
import gg.modl.backend.ticket.dto.response.TicketSubscriptionResponse;
import gg.modl.backend.ticket.service.TicketSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PANEL_TICKET_SUBSCRIPTIONS)
@RequiredArgsConstructor
public class TicketSubscriptionController {
    private final TicketSubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<TicketSubscriptionResponse>> getSubscriptions(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get staff email from session
        String staffEmail = "";

        if (staffEmail.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        List<TicketSubscriptionResponse> subscriptions = subscriptionService.getSubscriptions(server, staffEmail);
        return ResponseEntity.ok(subscriptions);
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<?> unsubscribe(
            @PathVariable String ticketId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get staff email from session
        String staffEmail = "";

        if (staffEmail.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean result = subscriptionService.unsubscribe(server, staffEmail, ticketId);
        if (result) {
            return ResponseEntity.ok(Map.of("message", "Successfully unsubscribed from ticket"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/updates")
    public ResponseEntity<List<SubscriptionUpdateResponse>> getUpdates(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get staff email from session
        String staffEmail = "";

        if (staffEmail.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        List<SubscriptionUpdateResponse> updates = subscriptionService.getUpdates(server, staffEmail, limit);
        return ResponseEntity.ok(updates);
    }

    @PostMapping("/updates/{updateId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable String updateId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        // TODO: Get staff email from session
        String staffEmail = "";

        if (staffEmail.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean result = subscriptionService.markAsRead(server, staffEmail, updateId);
        return ResponseEntity.ok(Map.of("message", "Update marked as read", "modified", result));
    }
}
