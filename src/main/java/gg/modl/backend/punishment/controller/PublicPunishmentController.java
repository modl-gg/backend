package gg.modl.backend.punishment.controller;

import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.player.dto.response.AppealEligibility;
import gg.modl.backend.player.service.PunishmentQueryService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        AppealEligibility eligibility = punishmentQueryService.getPublicPunishmentWithAppealEligibility(server, punishmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Punishment not found"));

        return switch (eligibility) {
            case AppealEligibility.NotStarted notStarted ->
                ResponseEntity.badRequest().body(Map.of("error", notStarted.message()));
            case AppealEligibility.Eligible eligible ->
                ResponseEntity.ok(PublicPunishmentProtoMapper.toAppealInfo(eligible.info()));
        };
    }
}
