package gg.modl.backend.appeal.controller;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    @GetMapping("/punishment/{punishmentId}")
    public ResponseEntity<List<TicketResponse>> getAppealsByPunishment(
        @PathVariable String punishmentId,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<TicketResponse> appeals = appealService.getAppealsByPunishment(server, punishmentId);

        if (appeals.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(appeals);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getAppealById(
        @PathVariable String id,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        return appealService.getAppealById(server, id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<?> addReply(
        @PathVariable String id,
        @RequestBody @Valid AddAppealReplyRequest replyRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return appealService.addReply(server, id, replyRequest)
            .map(reply -> ResponseEntity.status(HttpStatus.CREATED).body(reply))
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
        @PathVariable String id,
        @RequestBody @Valid UpdateAppealStatusRequest statusRequest,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        return appealService.updateStatus(server, id, statusRequest)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
