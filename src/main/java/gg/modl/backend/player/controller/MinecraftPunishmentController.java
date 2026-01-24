package gg.modl.backend.player.controller;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.service.PlayerStatusCalculator;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.rest.RequestUtil;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.PunishmentTypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
@RequestMapping(RESTMappingV1.MINECRAFT_PUNISHMENTS)
@RequiredArgsConstructor
public class MinecraftPunishmentController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;

    @PostMapping("/create")
    public ResponseEntity<Void> createPunishment(
            @RequestBody @Valid CreatePunishmentRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        createPunishmentInternal(server, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dynamic")
    public ResponseEntity<Map<String, Object>> createPunishmentDynamic(
            @RequestBody @Valid CreatePunishmentRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        String punishmentId = createPunishmentInternal(server, request);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Punishment created",
                "punishmentId", punishmentId
        ));
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Void> acknowledgePunishment(
            @RequestBody AcknowledgeRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(
                Criteria.where("minecraftUuid").is(request.playerUuid())
                        .and("punishments.id").is(request.punishmentId())
        );

        Update update = new Update()
                .set("punishments.$.started", new Date())
                .set("punishments.$.data.acknowledgedAt", request.executedAt())
                .set("punishments.$.data.acknowledgedSuccess", request.success());

        if (request.errorMessage() != null) {
            update.set("punishments.$.data.acknowledgeError", request.errorMessage());
        }

        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{punishmentId}/pardon")
    public ResponseEntity<Map<String, Object>> pardonPunishment(
            @PathVariable String punishmentId,
            @RequestBody PardonRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_PARDON",
                new Date(),
                request.issuerName(),
                request.reason() != null ? request.reason() : "",
                null,
                null,
                null
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .set("punishments.$.data.active", false);

        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Punishment pardoned"
        ));
    }

    @PostMapping("/{punishmentId}/note")
    public ResponseEntity<Map<String, Object>> addNote(
            @PathVariable String punishmentId,
            @RequestBody @Valid AddNoteRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        PunishmentNote note = new PunishmentNote(new ObjectId().toHexString(), request.note(), new Date(), request.issuerName());

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update().push("punishments.$.notes", note);
        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Note added"
        ));
    }

    @PostMapping("/{punishmentId}/evidence")
    public ResponseEntity<Map<String, Object>> addEvidence(
            @PathVariable String punishmentId,
            @RequestBody @Valid AddEvidenceRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        PunishmentEvidence evidence = new PunishmentEvidence(
                null,
                request.evidenceUrl(),
                "url",
                request.issuerName(),
                new Date(),
                null, null, null
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update().push("punishments.$.evidence", evidence);
        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Evidence added"
        ));
    }

    @PostMapping("/{punishmentId}/duration")
    public ResponseEntity<Map<String, Object>> changeDuration(
            @PathVariable String punishmentId,
            @RequestBody ChangeDurationRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "DURATION_CHANGE",
                new Date(),
                request.issuerName(),
                "Duration changed",
                request.newDuration(),
                null,
                null
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .set("punishments.$.data.duration", request.newDuration());

        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Duration changed"
        ));
    }

    @PostMapping("/{punishmentId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleOption(
            @PathVariable String punishmentId,
            @RequestBody ToggleOptionRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("punishments.id").is(punishmentId));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        String fieldName = switch (request.option()) {
            case "ALT_BLOCKING" -> "punishments.$.data.altBlocking";
            case "STAT_WIPE" -> "punishments.$.data.wipeAfterExpiry";
            default -> null;
        };

        if (fieldName == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", 400,
                    "message", "Invalid option"
            ));
        }

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update().set(fieldName, request.enabled());
        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "message", "Option toggled"
        ));
    }

    private String createPunishmentInternal(Server server, CreatePunishmentRequest request) {
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Query query = Query.query(Criteria.where("minecraftUuid").is(request.targetUuid()));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }

        Date now = new Date();
        Map<String, Object> data = request.data() != null ? new HashMap<>(request.data()) : new HashMap<>();

        if (request.severity() != null) {
            data.put("severity", request.severity());
        }
        if (request.status() != null) {
            data.put("status", request.status());
        }
        if (request.duration() != null) {
            data.put("duration", request.duration());
        }
        data.putIfAbsent("active", true);

        List<PunishmentNote> notes = new ArrayList<>();
        if (request.notes() != null) {
            for (String noteText : request.notes()) {
                notes.add(new PunishmentNote(new ObjectId().toHexString(), noteText, now, request.issuerName()));
            }
        }

        String punishmentId = new ObjectId().toHexString();

        Punishment punishment = new Punishment(
                punishmentId,
                request.typeOrdinal(),
                request.issuerName(),
                now,
                new ArrayList<>(),
                notes,
                new ArrayList<>(),
                request.attachedTicketIds() != null ? request.attachedTicketIds() : new ArrayList<>(),
                data
        );

        Update update = new Update().push("punishments", punishment);
        template.updateFirst(query, update, Player.class, CollectionName.PLAYERS);

        return punishmentId;
    }

    public record CreatePunishmentRequest(
            @NotBlank String targetUuid,
            @NotBlank String issuerName,
            int typeOrdinal,
            String reason,
            Long duration,
            Map<String, Object> data,
            List<String> notes,
            List<String> attachedTicketIds,
            String severity,
            String status
    ) {}

    public record AcknowledgeRequest(
            String punishmentId,
            String playerUuid,
            String executedAt,
            boolean success,
            String errorMessage
    ) {}

    public record PardonRequest(
            String issuerName,
            String reason,
            String expectedType
    ) {}

    public record AddNoteRequest(
            @NotBlank String issuerName,
            @NotBlank String note
    ) {}

    public record AddEvidenceRequest(
            @NotBlank String issuerName,
            @NotBlank String evidenceUrl
    ) {}

    public record ChangeDurationRequest(
            @NotBlank String issuerName,
            Long newDuration
    ) {}

    public record ToggleOptionRequest(
            @NotBlank String issuerName,
            @NotBlank String option,
            boolean enabled
    ) {}
}
