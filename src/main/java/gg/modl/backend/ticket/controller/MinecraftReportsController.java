package gg.modl.backend.ticket.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.dto.request.AssignReportRequest;
import gg.modl.backend.ticket.dto.request.DismissReportRequest;
import gg.modl.backend.ticket.dto.request.ResolveReportRequest;
import gg.modl.backend.ticket.dto.response.MinecraftReportView;
import gg.modl.backend.ticket.dto.response.MinecraftV1Response;
import gg.modl.backend.ticket.service.MinecraftTicketService;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_REPORTS)
@RequiredArgsConstructor
@Validated
public class MinecraftReportsController {
    private final MinecraftTicketService minecraftTicketService;

    @GetMapping
    public ResponseEntity<MinecraftV1Response> getAllReports(
        @RequestParam(defaultValue = "open") String status,
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<MinecraftReportView> reports = minecraftTicketService.getMinecraftReports(server, status, limit);
        return ResponseEntity.ok(new MinecraftV1Response.ReportList(200, reports));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<MinecraftV1Response> dismissReport(
        @PathVariable String id,
        @RequestBody @Valid DismissReportRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftTicketService.ReportOperationResult result = minecraftTicketService.dismissMinecraftReport(server, id, request);
        if (result.status() == MinecraftTicketService.ReportOperationStatus.NOT_FOUND) {
            return reportNotFound();
        }

        return ResponseEntity.ok(new MinecraftV1Response.ReportOperation(200, true, "Report dismissed"));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<MinecraftV1Response> resolveReport(
        @PathVariable String id,
        @RequestBody @Valid ResolveReportRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftTicketService.ReportOperationResult result = minecraftTicketService.resolveMinecraftReport(server, id, request);
        if (result.status() == MinecraftTicketService.ReportOperationStatus.NOT_FOUND) {
            return reportNotFound();
        }

        return ResponseEntity.ok(new MinecraftV1Response.ReportOperation(200, true, "Report resolved"));
    }

    @GetMapping("/player/{uuid}")
    public ResponseEntity<MinecraftV1Response> getPlayerReports(
        @PathVariable String uuid,
        @RequestParam(defaultValue = "all") String status,
        @RequestParam(defaultValue = "50") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        List<MinecraftReportView> reports = minecraftTicketService.getMinecraftReportsForPlayer(server, uuid, status, limit);
        return ResponseEntity.ok(new MinecraftV1Response.ReportList(200, reports));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<MinecraftV1Response> assignReport(
        @PathVariable String id,
        @RequestBody @Valid AssignReportRequest request,
        HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MinecraftTicketService.ReportOperationResult result = minecraftTicketService.assignMinecraftReport(server, id, request);
        if (result.status() == MinecraftTicketService.ReportOperationStatus.NOT_FOUND) {
            return reportNotFound();
        }

        return ResponseEntity.ok(new MinecraftV1Response.ReportOperation(200, true, "Report assigned"));
    }

    private ResponseEntity<MinecraftV1Response> reportNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new MinecraftV1Response.NotFound(404, "Report not found"));
    }
}
