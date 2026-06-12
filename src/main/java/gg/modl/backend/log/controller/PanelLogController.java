package gg.modl.backend.log.controller;

import gg.modl.backend.log.dto.response.SystemLogResponse;
import gg.modl.backend.log.service.LogService;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.proto.modl.v1.PanelLogsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PANEL_LOGS)
@RequiredArgsConstructor
@Validated
public class PanelLogController {
    private final LogService logService;

    @GetMapping
    public ResponseEntity<PanelLogsResponse> getLogs(
        @RequestParam(defaultValue = "100") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest request
    ) {
        Server server = RequestUtil.getRequestServer(request);
        List<SystemLogResponse> logs = logService.getLogs(server, limit);
        return ResponseEntity.ok(LogProtoMapper.toPanelLogsResponse(logs));
    }
}
