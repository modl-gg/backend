package gg.modl.backend.staff.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.staff.data.Staff;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class MinecraftStaff2faController {

    private final DynamicMongoTemplateProvider mongoProvider;
    private final String modlDomain;

    public MinecraftStaff2faController(
            DynamicMongoTemplateProvider mongoProvider,
            @Value("${modl.domain:modl.gg}") String modlDomain) {
        this.mongoProvider = mongoProvider;
        this.modlDomain = modlDomain;
    }

    @PostMapping("/2fa/generate")
    public ResponseEntity<Map<String, Object>> generate2faToken(
            @RequestBody @Valid Generate2faRequest request,
            HttpServletRequest httpRequest) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        String token = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();

        // Store token directly on the Staff document
        Query staffQuery = Query.query(Criteria.where("assignedMinecraftUuid").is(request.minecraftUuid()));
        Update update = new Update()
                .set("twoFactorToken", token)
                .set("twoFactorTokenIp", request.ip())
                .set("twoFactorTokenCreatedAt", now);
        var result = template.updateFirst(staffQuery, update, Staff.class, CollectionName.STAFF);

        if (result.getMatchedCount() == 0) {
            return ResponseEntity.notFound().build();
        }

        // Build verify URL using server's panel domain
        String domain = server.getCustomDomainOverride();
        if (domain == null || domain.isBlank()) {
            domain = server.getCustomDomain() + "." + modlDomain;
        }
        String verifyUrl = "https://" + domain + "/verify/" + token;

        return ResponseEntity.ok(Map.of(
                "token", token,
                "verifyUrl", verifyUrl
        ));
    }

    public record Generate2faRequest(
            @NotBlank String minecraftUuid,
            @NotBlank String ip
    ) {}
}
