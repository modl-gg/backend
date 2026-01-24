package gg.modl.backend.role.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_ROLES)
@RequiredArgsConstructor
public class MinecraftRolesController {
    private final DynamicMongoTemplateProvider mongoProvider;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRoles(HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = new Query();
        query.with(Sort.by(Sort.Direction.ASC, "order"));

        List<StaffRole> roles = template.find(query, StaffRole.class, CollectionName.STAFF_ROLES);

        List<Map<String, Object>> roleList = roles.stream().map(role -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", role.getId());
            r.put("name", role.getName());
            r.put("description", role.getDescription());
            r.put("permissions", role.getPermissions());
            r.put("isDefault", role.isDefault());
            r.put("order", role.getOrder());
            r.put("createdAt", role.getCreatedAt());
            r.put("updatedAt", role.getUpdatedAt());
            return r;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "roles", roleList
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRole(
            @PathVariable String id,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        StaffRole role = template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Role not found"
            ));
        }

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("id", role.getId());
        roleData.put("name", role.getName());
        roleData.put("description", role.getDescription());
        roleData.put("permissions", role.getPermissions());
        roleData.put("isDefault", role.isDefault());
        roleData.put("order", role.getOrder());
        roleData.put("createdAt", role.getCreatedAt());
        roleData.put("updatedAt", role.getUpdatedAt());

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "role", roleData
        ));
    }

    @PatchMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> updateRolePermissions(
            @PathVariable String id,
            @RequestBody @Valid UpdatePermissionsRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("id").is(id));
        StaffRole role = template.findOne(query, StaffRole.class, CollectionName.STAFF_ROLES);

        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Role not found"
            ));
        }

        Update update = new Update()
                .set("permissions", request.permissions())
                .set("updatedAt", new Date());

        template.updateFirst(query, update, StaffRole.class, CollectionName.STAFF_ROLES);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Role permissions updated"
        ));
    }

    public record UpdatePermissionsRequest(
            List<String> permissions
    ) {}
}
