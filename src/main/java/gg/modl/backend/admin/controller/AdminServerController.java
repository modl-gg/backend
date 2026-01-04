package gg.modl.backend.admin.controller;

import gg.modl.backend.admin.service.AdminServerService;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.data.ProvisioningStatus;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.server.data.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.ADMIN_SERVERS)
@RequiredArgsConstructor
@Slf4j
public class AdminServerController {
    private final AdminServerService serverService;

    @GetMapping
    public ResponseEntity<?> getServers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        int pageNum = Math.max(1, page);
        int limitNum = Math.min(100, Math.max(1, limit));
        int skip = (pageNum - 1) * limitNum;

        List<Server> servers = serverService.findServers(search, plan, status, sort, order, skip, limitNum);
        long total = serverService.countServers(search, plan, status);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "servers", servers,
                        "pagination", Map.of(
                                "page", pageNum,
                                "limit", limitNum,
                                "total", total,
                                "pages", (int) Math.ceil((double) total / limitNum)
                        )
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getServer(@PathVariable String id) {
        return serverService.findById(id)
                .map(server -> ResponseEntity.ok(Map.of("success", true, "data", server)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found")));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getServerStats(@PathVariable String id) {
        return serverService.findById(id)
                .map(server -> {
                    Map<String, Object> stats = serverService.getServerStats(server);
                    return ResponseEntity.ok(Map.of("success", true, "data", stats));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found")));
    }

    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody @Valid CreateServerRequest request) {
        Date now = new Date();
        Server server = new Server(
                request.serverName(),
                request.customDomain(),
                "server_" + request.customDomain(),
                request.adminEmail(),
                false,
                ProvisioningStatus.pending,
                request.plan() != null ? ServerPlan.valueOf(request.plan()) : ServerPlan.free,
                SubscriptionStatus.inactive,
                now,
                now
        );

        try {
            Server saved = serverService.save(server);
            return ResponseEntity.status(201).body(Map.of(
                    "success", true,
                    "data", saved,
                    "message", "Server created successfully"
            ));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                return ResponseEntity.status(409).body(Map.of("success", false, "error", "Server name or domain already exists"));
            }
            log.error("Failed to create server", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to create server"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateServer(@PathVariable String id, @RequestBody Map<String, Object> updateData) {
        if (!serverService.findById(id).isPresent()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found"));
        }

        updateData.remove("_id");
        updateData.remove("createdAt");
        updateData.remove("serverName");
        updateData.remove("customDomain");
        updateData.remove("databaseName");
        updateData.put("updatedAt", new Date());

        Server updated = serverService.updateById(id, updateData);
        if (updated != null) {
            return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "Server updated successfully"));
        }
        return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to update server"));
    }

    @PutMapping("/{id}/stats")
    public ResponseEntity<?> updateServerStats(@PathVariable String id, @RequestBody UpdateStatsRequest request) {
        if (!serverService.findById(id).isPresent()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found"));
        }

        Map<String, Object> updateData = new HashMap<>();
        if (request.lastActivityAt() != null) {
            updateData.put("lastActivityAt", request.lastActivityAt());
        }
        updateData.put("updatedAt", new Date());

        Server updated = serverService.updateById(id, updateData);
        if (updated != null) {
            return ResponseEntity.ok(Map.of("success", true, "data", updated, "message", "Server activity updated successfully"));
        }
        return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to update server"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteServer(@PathVariable String id) {
        if (serverService.deleteById(id)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Server deleted successfully"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found"));
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkOperation(@RequestBody BulkOperationRequest request) {
        if (request.action() == null || request.serverIds() == null || request.serverIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing required fields: action, serverIds"));
        }

        long affectedCount = switch (request.action()) {
            case "delete" -> serverService.bulkDelete(request.serverIds());
            case "suspend" -> serverService.bulkSuspend(request.serverIds());
            case "activate" -> serverService.bulkActivate(request.serverIds());
            case "update-plan" -> {
                if (request.parameters() == null || !request.parameters().containsKey("plan")) {
                    yield -1L;
                }
                yield serverService.bulkUpdatePlan(request.serverIds(), (String) request.parameters().get("plan"));
            }
            default -> -1L;
        };

        if (affectedCount < 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid action or missing parameters"));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("action", request.action(), "affectedCount", affectedCount, "serverIds", request.serverIds()),
                "message", "Bulk operation '" + request.action() + "' completed successfully"
        ));
    }

    @PostMapping("/{id}/reset-database")
    public ResponseEntity<?> resetDatabase(@PathVariable String id) {
        return serverService.findById(id)
                .map(server -> {
                    serverService.resetServerDatabase(server);
                    log.info("Server {} reset to provisioning state by admin", server.getServerName());
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Server reset to provisioning state. The provisioning system will reinitialize the database."
                    ));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found")));
    }

    @PostMapping("/{id}/export-data")
    public ResponseEntity<?> exportData(@PathVariable String id) {
        return serverService.findById(id)
                .map(server -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Data export initiated. You will receive an email with the download link."
                )))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "error", "Server not found")));
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchServers(@RequestBody SearchRequest request) {
        String query = request.query() != null ? request.query() : "";
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();

        String plan = filters.get("plan") != null ? filters.get("plan").toString() : null;
        String status = filters.get("status") != null ? filters.get("status").toString() : null;

        List<Server> servers = serverService.findServers(query, plan, status, "createdAt", "desc", 0, 50);
        long total = serverService.countServers(query, plan, status);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "servers", servers,
                        "total", total
                )
        ));
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportServers(@RequestBody ExportRequest request) {
        String format = request.format() != null ? request.format() : "json";
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();

        String plan = filters.get("plan") != null ? filters.get("plan").toString() : null;
        String status = filters.get("status") != null ? filters.get("status").toString() : null;

        List<Server> servers = serverService.findServers(null, plan, status, "createdAt", "desc", 0, 10000);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            csv.append("id,serverName,customDomain,adminEmail,plan,provisioningStatus,emailVerified,createdAt\n");
            for (Server s : servers) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                        s.getId(), s.getServerName(), s.getCustomDomain(), s.getAdminEmail(),
                        s.getPlan(), s.getProvisioningStatus(), s.getEmailVerified(), s.getCreatedAt()));
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=servers-export.csv")
                    .body(csv.toString());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "servers", servers,
                        "exportedAt", new Date(),
                        "format", format,
                        "count", servers.size()
                )
        ));
    }

    // Request records
    public record SearchRequest(String query, Map<String, Object> filters) {}
    public record ExportRequest(String format, Map<String, Object> filters) {}
    public record CreateServerRequest(
            @NotBlank String serverName,
            @NotBlank String customDomain,
            @Email @NotBlank String adminEmail,
            String plan
    ) {}

    public record UpdateStatsRequest(Integer userCount, Integer ticketCount, Date lastActivityAt) {}

    public record BulkOperationRequest(String action, List<String> serverIds, Map<String, Object> parameters) {}
}
