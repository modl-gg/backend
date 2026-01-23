package gg.modl.backend.staff.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_STAFF)
@RequiredArgsConstructor
public class MinecraftStaffController {
    private final DynamicMongoTemplateProvider mongoProvider;

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
}
