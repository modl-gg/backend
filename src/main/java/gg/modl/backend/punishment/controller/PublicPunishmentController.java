package gg.modl.backend.punishment.controller;

import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_PUNISHMENT)
@RequiredArgsConstructor
public class PublicPunishmentController {
    private final PunishmentQueryService punishmentQueryService;

    @GetMapping("/{punishmentId}/appeal-info")
    public ResponseEntity<?> getAppealInfo(
            @PathVariable String punishmentId,
            HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);

        var resultOpt = punishmentQueryService.getPublicPunishmentWithAppealEligibility(server, punishmentId);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = resultOpt.get();
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
    }
}
