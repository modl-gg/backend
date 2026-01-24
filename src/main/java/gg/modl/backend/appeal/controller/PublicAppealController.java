package gg.modl.backend.appeal.controller;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.CreateAppealRequest;
import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
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
@RequestMapping(RESTMappingV1.PUBLIC_APPEALS)
@RequiredArgsConstructor
public class PublicAppealController {
    private final AppealService appealService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getAppeal(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return appealService.getAppealById(server, id)
                .map(appeal -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", appeal.id());
                    response.put("_id", appeal.id());
                    response.put("type", appeal.type());
                    response.put("subject", appeal.subject());
                    response.put("status", appeal.status());
                    response.put("creator", appeal.creator() != null ? appeal.creator() : "");
                    response.put("creatorUuid", appeal.creatorUuid() != null ? appeal.creatorUuid() : "");
                    response.put("created", appeal.date());
                    response.put("date", appeal.date());
                    response.put("locked", appeal.locked());
                    response.put("replies", appeal.messages() != null ? appeal.messages() : Collections.emptyList());
                    response.put("messages", appeal.messages() != null ? appeal.messages() : Collections.emptyList());
                    response.put("notes", appeal.notes() != null ? appeal.notes() : Collections.emptyList());
                    response.put("tags", appeal.tags() != null ? appeal.tags() : Collections.emptyList());
                    response.put("data", appeal.data() != null ? appeal.data() : Map.of());
                    return ResponseEntity.ok(response);
                })
                .<ResponseEntity<?>>map(r -> r)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Appeal not found")));
    }

    @PostMapping
    public ResponseEntity<?> createAppeal(
            @RequestBody @Valid CreateAppealRequest createRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            TicketResponse appeal = appealService.createAppeal(server, createRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "appealId", appeal.id(),
                    "message", "Appeal created successfully",
                    "appeal", Map.of(
                            "id", appeal.id(),
                            "_id", appeal.id(),
                            "type", appeal.type(),
                            "subject", appeal.subject(),
                            "status", appeal.status(),
                            "created", appeal.date().toInstant().toString()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", "Failed to create appeal"));
        }
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
            @PathVariable String id,
            @RequestBody @Valid AddAppealReplyRequest replyRequest,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        try {
            Optional<TicketReply> replyOpt = appealService.addReply(server, id, replyRequest);

            if (replyOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Appeal not found"));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Reply added successfully",
                    "reply", replyOpt.get()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
