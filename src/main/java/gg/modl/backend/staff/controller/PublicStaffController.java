package gg.modl.backend.staff.controller;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
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
import java.util.HashMap;
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

    private static final String TOKENS_COLLECTION = "staff_2fa_tokens";
    private static final String VERIFICATIONS_COLLECTION = "staff_2fa_verifications";

    @PostMapping("/2fa/verify/{token}")
    public ResponseEntity<Map<String, Object>> verify2faToken(
            @PathVariable String token,
            HttpServletRequest request) {
        Server server = RequestUtil.getRequestServer(request);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Find the unverified token
        Query query = Query.query(Criteria.where("token").is(token).and("verified").is(false));
        Map tokenDoc = template.findOne(query, Map.class, TOKENS_COLLECTION);

        if (tokenDoc == null) {
            return ResponseEntity.notFound().build();
        }

        // Mark as verified
        Update update = new Update();
        update.set("verified", true);
        update.set("verifiedAt", Instant.now().toEpochMilli());
        template.updateFirst(query, update, TOKENS_COLLECTION);

        // Store the verification so the sync response can deliver it to the plugin
        Map<String, Object> verification = new HashMap<>();
        verification.put("minecraftUuid", tokenDoc.get("minecraftUuid"));
        verification.put("ip", tokenDoc.get("ip"));
        verification.put("verifiedAt", Instant.now().toEpochMilli());
        verification.put("delivered", false);
        template.save(verification, VERIFICATIONS_COLLECTION);

        return ResponseEntity.ok(Map.of("status", "verified"));
    }
}
