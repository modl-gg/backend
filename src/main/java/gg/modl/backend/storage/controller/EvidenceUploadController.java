package gg.modl.backend.storage.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.server.data.ServerPlan;
import gg.modl.backend.storage.service.EvidenceUploadTokenService;
import gg.modl.backend.storage.service.EvidenceUploadTokenService.UploadToken;
import gg.modl.backend.storage.service.MediaValidationService;
import gg.modl.backend.storage.service.S3StorageService;
import gg.modl.backend.storage.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_EVIDENCE_UPLOAD)
@RequiredArgsConstructor
@Slf4j
public class EvidenceUploadController {

    private final EvidenceUploadTokenService tokenService;
    private final S3StorageService s3StorageService;
    private final DynamicMongoTemplateProvider mongoProvider;
    private final ServerService serverService;
    private final StorageQuotaService quotaService;
    private final MediaValidationService validationService;

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> validateToken(@PathVariable String token) {
        UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
        }

        // Get player name
        MongoTemplate template = mongoProvider.getFromDatabaseName(uploadToken.serverDatabaseName());
        Query query = Query.query(Criteria.where("minecraftUuid").is(uploadToken.playerUuid()));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);
        String playerName = "Unknown";
        if (player != null && !player.getUsernames().isEmpty()) {
            playerName = player.getUsernames().get(player.getUsernames().size() - 1).username();
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "punishmentId", uploadToken.punishmentId(),
                "playerName", playerName,
                "issuerName", uploadToken.issuerName()
        ));
    }

    @PostMapping("/{token}/presign")
    public ResponseEntity<Map<String, Object>> presignUpload(
            @PathVariable String token,
            @RequestBody Map<String, Object> body
    ) {
        UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
        }

        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", 503,
                    "message", "File storage is not configured"
            ));
        }

        String fileName = (String) body.get("fileName");
        String contentType = (String) body.get("contentType");
        long fileSize = ((Number) body.get("fileSize")).longValue();

        Server server = serverService.getServerByDatabaseName(uploadToken.serverDatabaseName());
        if (server == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Server not found"
            ));
        }

        boolean isPremium = server.getPlan() == ServerPlan.premium;
        MediaValidationService.ValidationResult validation = validationService.validateMetadata(
                fileName, contentType, fileSize, "evidence", isPremium);
        if (!validation.valid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", validation.error()
            ));
        }

        if (!quotaService.canUpload(server, fileSize)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Storage quota exceeded. Please contact the server administrator."
            ));
        }

        var presign = s3StorageService.createPresignedUploadUrl(server, "evidence", fileName, contentType, fileSize, uploadToken.punishmentId());

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "presignedUrl", presign.presignedUrl(),
                "key", presign.key(),
                "expiresAt", presign.expiresAt().toString(),
                "method", presign.method(),
                "requiredHeaders", presign.requiredHeaders()
        ));
    }

    @PostMapping("/{token}/confirm")
    public ResponseEntity<Map<String, Object>> confirmUpload(
            @PathVariable String token,
            @RequestBody Map<String, String> body
    ) {
        UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
        }

        String key = body.get("key");
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Missing key"
            ));
        }

        var uploadDetails = s3StorageService.getUploadDetails(key);
        if (uploadDetails == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Upload not found. File may not have been uploaded yet."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "key", uploadDetails.key(),
                "url", uploadDetails.url(),
                "fileName", uploadDetails.fileName(),
                "size", uploadDetails.size(),
                "contentType", uploadDetails.contentType()
        ));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<Map<String, Object>> submitEvidence(
            @PathVariable String token,
            @RequestBody SubmitEvidenceRequest request
    ) {
        UploadToken uploadToken = tokenService.validateToken(token);
        if (uploadToken == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Invalid or expired upload token"
            ));
        }

        if (request.evidence() == null || request.evidence().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "No evidence provided"
            ));
        }

        MongoTemplate template = mongoProvider.getFromDatabaseName(uploadToken.serverDatabaseName());
        Date now = new Date();

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(uploadToken.playerUuid())
                        .and("punishments.id").is(uploadToken.punishmentId())
        );

        for (EvidenceItem item : request.evidence()) {
            PunishmentEvidence evidence = new PunishmentEvidence(
                    null,
                    item.url(),
                    "file",
                    uploadToken.issuerName(),
                    now,
                    item.fileName(),
                    item.fileType(),
                    item.fileSize()
            );

            Update update = new Update()
                    .push("punishments.$.evidence", evidence);
            template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);
        }

        // Add a note about the evidence upload
        PunishmentNote note = new PunishmentNote(
                new ObjectId().toHexString(),
                "uploaded " + request.evidence().size() + " evidence file(s)",
                now,
                uploadToken.issuerName()
        );
        Update noteUpdate = new Update().push("punishments.$.notes", note);
        template.updateFirst(updateQuery, noteUpdate, Player.class, CollectionName.PLAYERS);

        // Invalidate the token after successful submission
        tokenService.invalidateToken(token);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Evidence uploaded successfully"
        ));
    }

    public record SubmitEvidenceRequest(List<EvidenceItem> evidence) {}

    public record EvidenceItem(String url, String fileName, String fileType, Long fileSize) {}
}
