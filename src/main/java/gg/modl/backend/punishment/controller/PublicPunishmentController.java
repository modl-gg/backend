package gg.modl.backend.punishment.controller;

import gg.modl.backend.appeal.service.AppealService;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.player.service.PunishmentService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_PUNISHMENT)
@RequiredArgsConstructor
public class PublicPunishmentController {
    private final PunishmentService punishmentService;
    private final PunishmentTypeService punishmentTypeService;
    private final AppealService appealService;
    private final PlayerStatusCalculator statusCalculator;

    @GetMapping("/{punishmentId}/appeal-info")
    public ResponseEntity<?> getAppealInfo(
            @PathVariable String punishmentId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        var punishmentOpt = punishmentService.getPunishmentById(server, punishmentId);
        if (punishmentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var punishment = punishmentOpt.get();

        // Check if punishment has been started (has a started date)
        if (punishment.started() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "This punishment has not been started yet and cannot be appealed at this time."));
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("id", punishment.id());
        response.put("type", punishment.type());
        response.put("issued", punishment.issued());
        response.put("expires", punishment.expires());
        response.put("active", punishment.active());
        response.put("appealable", punishment.isAppealable());
        response.put("playerUuid", punishment.playerUuid());

        // Check for existing appeals
        List<TicketResponse> existingAppeals = appealService.getAppealsByPunishment(server, punishmentId);
        if (!existingAppeals.isEmpty()) {
            TicketResponse latestAppeal = existingAppeals.get(0);
            Map<String, Object> existingAppeal = new HashMap<>();
            existingAppeal.put("id", latestAppeal.id());
            existingAppeal.put("submittedDate", latestAppeal.date());
            existingAppeal.put("status", latestAppeal.status());
            response.put("existingAppeal", existingAppeal);
        }

        // Get appeal form configuration if available
        var punishmentType = punishmentTypeService.getPunishmentTypeByOrdinal(server, punishment.typeOrdinal());
        if (punishmentType.isPresent()) {
            // Appeal form would be part of punishment type settings if configured
            // For now, return null to let frontend use default form
            response.put("appealForm", null);
        }

        return ResponseEntity.ok(response);
    }
}
