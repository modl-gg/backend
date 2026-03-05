package gg.modl.backend.staff.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.staff.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v1/public/staff")
@RequiredArgsConstructor
public class PublicStaffController {
    private final InvitationService invitationService;
    private final DynamicMongoTemplateProvider mongoProvider;

    @GetMapping("/invitations/accept")
    public ResponseEntity<?> acceptInvitationGet(
            @RequestParam(required = false) String token,
            HttpServletRequest request
    ) {
        return acceptInvitationInternal(token, request);
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<?> acceptInvitationPost(
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String resolvedToken = token;
        if ((resolvedToken == null || resolvedToken.isBlank()) && body != null) {
            resolvedToken = body.get("token");
        }

        return acceptInvitationInternal(resolvedToken, request);
    }

    private ResponseEntity<?> acceptInvitationInternal(String token, HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid invitation link."));
        }

        Server server = RequestUtil.getRequestServer(request);

        try {
            StaffResponse staff = invitationService.acceptInvitation(server, token);

            return ResponseEntity.ok(Map.of(
                    "message", "Invitation accepted successfully.",
                    "staffMember", Map.of(
                            "email", staff.email(),
                            "username", staff.username(),
                            "role", staff.role()
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Internal server error."));
        }
    }

    @PostMapping("/2fa/verify/{token}")
    public ResponseEntity<Map<String, Object>> verify2faToken(
            @PathVariable String token,
            HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Find the staff member with this pending token
        Query query = Query.query(Criteria.where("twoFactorToken").is(token));
        Staff staff = template.findOne(query, Staff.class, CollectionName.STAFF);

        if (staff == null) {
            return ResponseEntity.notFound().build();
        }

        // Add IP to verifiedIps, clear the token fields, and flag for sync delivery
        long now = Instant.now().toEpochMilli();
        Staff.VerifiedIp verifiedIp = new Staff.VerifiedIp(staff.getTwoFactorTokenIp(), now);

        Update update = new Update()
                .unset("twoFactorToken")
                .unset("twoFactorTokenIp")
                .unset("twoFactorTokenCreatedAt")
                .set("twoFactorPendingDelivery", true)
                .push("verifiedIps", verifiedIp);
        template.updateFirst(query, update, Staff.class, CollectionName.STAFF);

        return ResponseEntity.ok(Map.of("status", "verified"));
    }
}
