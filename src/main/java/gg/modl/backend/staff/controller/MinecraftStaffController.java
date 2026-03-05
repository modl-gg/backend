package gg.modl.backend.staff.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_STAFF)
@RequiredArgsConstructor
public class MinecraftStaffController {
    private final DynamicMongoTemplateProvider mongoProvider;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllStaff(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        List<Staff> allStaff = template.findAll(Staff.class, CollectionName.STAFF);

        // Build a map of minecraftUuid -> totalPlaytimeSeconds from linked Player documents
        Map<String, Long> playerPlaytimeMap = new HashMap<>();
        for (Staff staff : allStaff) {
            if (staff.getAssignedMinecraftUuid() != null && !staff.getAssignedMinecraftUuid().isEmpty()) {
                Query playerQuery = Query.query(Criteria.where("minecraftUuid").is(staff.getAssignedMinecraftUuid()));
                playerQuery.fields().include("data.totalPlaytimeSeconds");
                Document playerDoc = template.findOne(playerQuery, Document.class, CollectionName.PLAYERS);
                if (playerDoc != null) {
                    Document data = playerDoc.get("data", Document.class);
                    if (data != null) {
                        Object playtimeObj = data.get("totalPlaytimeSeconds");
                        if (playtimeObj instanceof Number) {
                            playerPlaytimeMap.put(staff.getAssignedMinecraftUuid(), ((Number) playtimeObj).longValue() * 1000L);
                        }
                    }
                }
            }
        }

        // Aggregate punishment counts per issuerName
        Map<String, Integer> punishmentCounts = new HashMap<>();
        try {
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.unwind("punishments"),
                    Aggregation.group("punishments.issuerName").count().as("count")
            );
            AggregationResults<Document> results = template.aggregate(
                    aggregation, CollectionName.PLAYERS, Document.class);
            for (Document doc : results.getMappedResults()) {
                String issuerName = doc.getString("_id");
                if (issuerName != null) {
                    punishmentCounts.put(issuerName, doc.getInteger("count", 0));
                }
            }
        } catch (Exception ignored) {
            // If aggregation fails (e.g., no players collection), continue with zero counts
        }

        List<Map<String, Object>> staffList = new ArrayList<>();

        for (Staff staff : allStaff) {
            Query roleQuery = Query.query(Criteria.where("name").is(staff.getRole()));
            StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

            List<String> permissions = role != null ? role.getPermissions() : List.of();

            // Look up punishment count by assignedMinecraftUsername first, then username
            int punishmentsIssuedCount = 0;
            if (staff.getAssignedMinecraftUsername() != null && punishmentCounts.containsKey(staff.getAssignedMinecraftUsername())) {
                punishmentsIssuedCount = punishmentCounts.get(staff.getAssignedMinecraftUsername());
            } else if (staff.getUsername() != null && punishmentCounts.containsKey(staff.getUsername())) {
                punishmentsIssuedCount = punishmentCounts.get(staff.getUsername());
            }

            Map<String, Object> staffData = new LinkedHashMap<>();
            staffData.put("id", staff.getId());
            staffData.put("username", staff.getUsername());
            staffData.put("email", staff.getEmail());
            staffData.put("role", staff.getRole());
            staffData.put("minecraftUuid", staff.getAssignedMinecraftUuid());
            staffData.put("minecraftUsername", staff.getAssignedMinecraftUsername());
            staffData.put("permissions", permissions);
            staffData.put("lastSeen", staff.getLastSeen());
            staffData.put("totalPlaytimeMs", playerPlaytimeMap.getOrDefault(staff.getAssignedMinecraftUuid(), 0L));
            staffData.put("punishmentsIssuedCount", punishmentsIssuedCount);
            staffData.put("createdAt", staff.getCreatedAt());
            staffData.put("updatedAt", staff.getUpdatedAt());
            staffList.add(staffData);
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "staff", staffList
        ));
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getStaffPermissions(HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query staffQuery = Query.query(
                Criteria.where("assignedMinecraftUuid").exists(true).ne(null).ne("")
        );
        List<Staff> staffWithMinecraft = template.find(staffQuery, Staff.class, CollectionName.STAFF);

        List<Map<String, Object>> staffList = new ArrayList<>();

        for (Staff staff : staffWithMinecraft) {
            Query roleQuery = Query.query(Criteria.where("name").is(staff.getRole()));
            StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

            List<String> permissions = role != null ? role.getPermissions() : List.of();

            staffList.add(Map.of(
                    "minecraftUuid", staff.getAssignedMinecraftUuid(),
                    "minecraftUsername", staff.getAssignedMinecraftUsername() != null ? staff.getAssignedMinecraftUsername() : "",
                    "staffUsername", staff.getUsername() != null ? staff.getUsername() : "",
                    "staffRole", staff.getRole() != null ? staff.getRole() : "",
                    "permissions", permissions,
                    "email", staff.getEmail() != null ? staff.getEmail() : ""
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "data", Map.of("staff", staffList)
        ));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateStaffRole(
            @PathVariable String id,
            @RequestBody @Valid UpdateRoleRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("_id").is(id));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Staff member not found"
            ));
        }

        // Verify the role exists
        Query roleQuery = Query.query(Criteria.where("name").is(request.role()));
        StaffRole role = template.findOne(roleQuery, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "message", "Role not found"
            ));
        }

        Update update = new Update()
                .set("role", request.role())
                .set("updatedAt", new Date());

        template.updateFirst(query, update, Staff.class, CollectionName.STAFF);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Staff role updated"
        ));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> staffDisconnect(
            @RequestBody @Valid StaffDisconnectRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("assignedMinecraftUuid").is(request.minecraftUuid()));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Staff member not found"
            ));
        }

        Update update = new Update()
                .set("lastSeen", new Date());

        template.updateFirst(query, update, Staff.class, CollectionName.STAFF);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true
        ));
    }

    public record UpdateRoleRequest(
            @NotBlank String role
    ) {}

    public record StaffDisconnectRequest(
            @NotBlank String minecraftUuid,
            long sessionDurationMs
    ) {}
}
