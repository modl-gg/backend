package gg.modl.backend.staff.controller;

import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_STAFF)
@RequiredArgsConstructor
@Slf4j
public class MinecraftStaff2faController {

    private final DynamicMongoTemplateProvider mongoProvider;

    private static final String COLLECTION = "staff_2fa_tokens";

    @PostMapping("/2fa/generate")
    public ResponseEntity<Map<String, Object>> generate2faToken(
            @RequestBody @Valid Generate2faRequest request,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        String token = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        // Store token in DB
        Map<String, Object> tokenDoc = Map.of(
                "token", token,
                "minecraftUuid", request.minecraftUuid(),
                "ip", request.ip(),
                "createdAt", now,
                "verified", false
        );
        template.save(tokenDoc, COLLECTION);

        // Build verify URL using server's panel domain
        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + ".modl.gg";
        }
        String verifyUrl = "https://" + domain + "/verify/" + token;

        return ResponseEntity.ok(Map.of(
                "token", token,
                "verifyUrl", verifyUrl
        ));
    }

    // Called from the panel when staff clicks the verification link
    @PostMapping("/2fa/verify/{token}")
    public ResponseEntity<Map<String, Object>> verifyToken(
            @PathVariable String token,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Find the token
        Query query = Query.query(Criteria.where("token").is(token).and("verified").is(false));
        Map tokenDoc = template.findOne(query, Map.class, COLLECTION);

        if (tokenDoc == null) {
            return ResponseEntity.notFound().build();
        }

        // Mark as verified
        Update update = new Update();
        update.set("verified", true);
        update.set("verifiedAt", Instant.now().toEpochMilli());
        template.updateFirst(query, update, COLLECTION);

        // Store the verification so the sync response can deliver it
        Map<String, Object> verification = Map.of(
                "minecraftUuid", tokenDoc.get("minecraftUuid"),
                "ip", tokenDoc.get("ip"),
                "verifiedAt", Instant.now().toEpochMilli(),
                "delivered", false
        );
        template.save(verification, "staff_2fa_verifications");

        return ResponseEntity.ok(Map.of("status", "verified"));
    }

    public record Generate2faRequest(
            @NotBlank String minecraftUuid,
            @NotBlank String ip
    ) {}
}
