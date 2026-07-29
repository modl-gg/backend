package gg.modl.backend.admin.controller;

import com.google.protobuf.Timestamp;
import gg.modl.backend.admin.service.AdminServerService;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.infrastructure.util.PaginationHelper;
import gg.modl.backend.infrastructure.validation.RequestValidationLimits;
import gg.modl.proto.modl.v1.AdminServerBulkOperationRequest;
import gg.modl.proto.modl.v1.AdminServerCreateRequest;
import gg.modl.proto.modl.v1.AdminServerExportRequest;
import gg.modl.proto.modl.v1.AdminServerSearchRequest;
import gg.modl.proto.modl.v1.AdminServerUpdateStatsRequest;
import gg.modl.proto.modl.v1.AdminServerUsageBatchRequest;
import gg.modl.proto.modl.v1.UpdateServerRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SERVERS)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminServerController {
    private final AdminServerService serverService;

    @GetMapping
    public ResponseEntity<?> getServers(
        @RequestParam(defaultValue = "1") @Min(RequestValidationLimits.PAGINATION_PAGE_MIN) int page,
        @RequestParam(defaultValue = "20") @Min(RequestValidationLimits.PAGINATION_LIMIT_MIN) @Max(RequestValidationLimits.PAGINATION_LIMIT_MAX) int limit,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String plan,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String order) {

        int pageNum = PaginationHelper.normalizePage(page);
        int limitNum = PaginationHelper.normalizeLimit(limit, 50);
        int skip = PaginationHelper.calculateSkip(page, limitNum);

        List<Server> servers = serverService.findServers(search, plan, status, sort, order, skip, limitNum);
        long total = serverService.countServers(search, plan, status);

        return ResponseEntity.ok(AdminServerProtoMapper.toListResponse(
            servers, pageNum, limitNum, total, PaginationHelper.calculateTotalPages(total, limitNum)));
    }

    @PostMapping("/usage/batch")
    public ResponseEntity<?> getUsageBatch(@RequestBody AdminServerUsageBatchRequest request) {
        List<String> serverIds = request.getServerIdsList();
        if (serverIds.isEmpty()) {
            throw new ValidationException("Missing required field: serverIds");
        }

        if (serverIds.size() > 50) {
            throw new ValidationException("Maximum 50 server IDs per request");
        }

        boolean forceRefresh = request.getForceRefresh();
        Map<String, AdminServerService.UsageSummary> usage = serverService.getUsageStatsForServerIds(serverIds, forceRefresh);

        return ResponseEntity.ok(AdminServerProtoMapper.toUsageBatchResponse(usage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getServer(@PathVariable String id) {
        return serverService.findById(id)
            .map(server -> ResponseEntity.ok((Object) AdminServerProtoMapper.toDetailResponse(server)))
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getServerStats(@PathVariable String id) {
        return serverService.findById(id)
            .map(server -> {
                Map<String, Object> stats = serverService.getServerStats(server);
                return ResponseEntity.ok((Object) AdminServerProtoMapper.toStatsResponse(stats));
            })
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
    }

    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody AdminServerCreateRequest request) {
        Server saved = serverService.createServer(
            request.getServerName(),
            request.getCustomDomain(),
            request.getAdminEmail(),
            request.hasPlan() ? request.getPlan() : null);
        return ResponseEntity.status(201).body(
            AdminServerProtoMapper.toMutationResponse(saved, "Server created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateServer(@PathVariable String id, @RequestBody UpdateServerRequest request) {
        Server server = serverService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));

        if (request.hasAdminEmail()) {
            serverService.changeAdminEmail(server, request.getAdminEmail());
        }

        Map<String, Object> updateData = new HashMap<>();
        if (request.hasEmailVerified()) {
            updateData.put("emailVerified", request.getEmailVerified());
        }
        if (request.hasProvisioningStatus()) {
            updateData.put("provisioningStatus", request.getProvisioningStatus());
        }
        if (request.hasProvisioningNotes()) {
            updateData.put("provisioningNotes", request.getProvisioningNotes());
        }
        if (request.hasPlan()) {
            updateData.put("plan", request.getPlan());
        }
        if (request.hasSubscriptionStatus()) {
            updateData.put("subscriptionStatus", request.getSubscriptionStatus());
        }
        if (request.hasLastActivityAt()) {
            updateData.put("lastActivityAt", request.getLastActivityAt());
        }
        updateData.put("updatedAt", new Date());

        Server updated = serverService.updateById(id, updateData);
        if (updated != null) {
            return ResponseEntity.ok(AdminServerProtoMapper.toMutationResponse(updated, "Server updated successfully"));
        }
        throw new ValidationException("Failed to update server");
    }

    @PutMapping("/{id}/stats")
    public ResponseEntity<?> updateServerStats(@PathVariable String id, @RequestBody AdminServerUpdateStatsRequest request) {
        if (serverService.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Server not found");
        }

        Map<String, Object> updateData = new HashMap<>();
        if (request.hasLastActivityAt()) {
            updateData.put("lastActivityAt", toDate(request.getLastActivityAt()));
        }
        updateData.put("updatedAt", new Date());

        Server updated = serverService.updateById(id, updateData);
        if (updated != null) {
            return ResponseEntity.ok(AdminServerProtoMapper.toMutationResponse(updated, "Server activity updated successfully"));
        }
        throw new ValidationException("Failed to update server");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteServer(@PathVariable String id) {
        if (serverService.deleteById(id)) {
            return ResponseEntity.ok(AdminServerProtoMapper.toMutationResponse(null, "Server deleted successfully"));
        }
        throw new ResourceNotFoundException("Server not found");
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkOperation(@RequestBody AdminServerBulkOperationRequest request) {
        List<String> serverIds = request.getServerIdsList();
        if (request.getAction().isEmpty() || serverIds.isEmpty()) {
            throw new ValidationException("Missing required fields: action, serverIds");
        }

        String action = request.getAction();
        long affectedCount = switch (action) {
            case "delete" -> serverService.bulkDelete(serverIds);
            case "suspend" -> serverService.bulkSuspend(serverIds);
            case "activate" -> serverService.bulkActivate(serverIds);
            case "update-plan" -> {
                if (!request.hasParameters() || !request.getParameters().hasPlan()) {
                    yield -1L;
                }
                yield serverService.bulkUpdatePlan(serverIds, request.getParameters().getPlan());
            }
            default -> -1L;
        };

        if (affectedCount < 0) {
            throw new ValidationException("Invalid action or missing parameters");
        }

        return ResponseEntity.ok(AdminServerProtoMapper.toBulkOperationResponse(
            action, affectedCount, serverIds, "Bulk operation '" + action + "' completed successfully"));
    }

    @PostMapping("/{id}/reset-database")
    public ResponseEntity<?> resetDatabase(@PathVariable String id) {
        return serverService.findById(id)
            .map(server -> {
                serverService.resetServerDatabase(server);
                log.info("Server {} reset to provisioning state by admin", server.getServerName());
                return ResponseEntity.ok((Object) AdminServerProtoMapper.toMutationResponse(null,
                    "Server reset to provisioning state. The provisioning system will reinitialize the database."));
            })
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
    }

    @PostMapping("/{id}/export-data")
    public ResponseEntity<?> exportData(@PathVariable String id) {
        return serverService.findById(id)
            .map(server -> ResponseEntity.ok((Object) AdminServerProtoMapper.toMutationResponse(null,
                "Data export initiated. You will receive an email with the download link.")))
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchServers(@RequestBody AdminServerSearchRequest request) {
        String query = request.hasQuery() ? request.getQuery() : "";
        String plan = request.hasFilters() && request.getFilters().hasPlan() ? request.getFilters().getPlan() : null;
        String status = request.hasFilters() && request.getFilters().hasStatus() ? request.getFilters().getStatus() : null;

        List<Server> servers = serverService.findServers(query, plan, status, "createdAt", "desc", 0, 50);
        long total = serverService.countServers(query, plan, status);

        return ResponseEntity.ok(AdminServerProtoMapper.toSearchResponse(servers, total));
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportServers(@RequestBody AdminServerExportRequest request) {
        String format = request.hasFormat() ? request.getFormat() : "json";
        String plan = request.hasFilters() && request.getFilters().hasPlan() ? request.getFilters().getPlan() : null;
        String status = request.hasFilters() && request.getFilters().hasStatus() ? request.getFilters().getStatus() : null;

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=servers-export.csv")
                .body(serverService.exportServersCsv(plan, status));
        }

        List<Server> servers = serverService.findServers(null, plan, status, "createdAt", "desc", 0, 10000);
        return ResponseEntity.ok(AdminServerProtoMapper.toExportResponse(servers, new Date(), format, servers.size()));
    }

    private static Date toDate(Timestamp timestamp) {
        return new Date(timestamp.getSeconds() * 1000L + timestamp.getNanos() / 1_000_000L);
    }
}
