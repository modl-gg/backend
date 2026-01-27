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

import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.util.IdGenerator;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.player.dto.response.PunishmentPreviewResponse;

import java.util.*;

@RestController
@RequestMapping(RESTMappingV1.MINECRAFT_PUNISHMENTS)
@RequiredArgsConstructor
public class MinecraftPunishmentController {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;

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

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentPunishments(
            @RequestParam(defaultValue = "48") int hours,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        Date cutoff = new Date(System.currentTimeMillis() - (hours * 60L * 60L * 1000L));

        Query query = Query.query(Criteria.where("punishments.issued").gte(cutoff));
        query.limit(100);

        List<Player> players = template.find(query, Player.class, CollectionName.PLAYERS);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        List<Map<String, Object>> punishments = new ArrayList<>();
        for (Player player : players) {
            String username = player.getUsernames().isEmpty() ? "Unknown"
                    : player.getUsernames().get(player.getUsernames().size() - 1).username();

            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getIssued() != null && punishment.getIssued().after(cutoff)) {
                    String typeName = types.stream()
                            .filter(t -> t.getOrdinal() == punishment.getType_ordinal())
                            .findFirst()
                            .map(PunishmentType::getName)
                            .orElse("Unknown");

                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", punishment.getId());
                    p.put("type", typeName);
                    p.put("playerName", username);
                    p.put("playerUuid", player.getMinecraftUuid().toString());
                    p.put("issuerName", punishment.getIssuerName());
                    p.put("issuedAt", punishment.getIssued());
                    p.put("reason", punishment.getData() != null ? punishment.getData().get("reason") : null);
                    p.put("active", statusCalculator.isPunishmentActive(punishment));
                    punishments.add(p);
                }
            }
        }

        punishments.sort((a, b) -> ((Date) b.get("issuedAt")).compareTo((Date) a.get("issuedAt")));

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "punishments", punishments
        ));
    }

    @GetMapping("/preview")
    public ResponseEntity<PunishmentPreviewResponse> previewPunishment(
            @RequestParam String playerUuid,
            @RequestParam int typeOrdinal,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Find the player
        Query query = Query.query(Criteria.where("minecraftUuid").is(playerUuid));
        Player player = template.findOne(query, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.ok(PunishmentPreviewResponse.error("Player not found"));
        }

        // Get punishment types
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        PunishmentType punishmentType = types.stream()
                .filter(t -> t.getOrdinal() == typeOrdinal)
                .findFirst()
                .orElse(null);

        if (punishmentType == null) {
            return ResponseEntity.ok(PunishmentPreviewResponse.error("Punishment type not found"));
        }

        // Get offender threshold settings from database
        OffenderThresholdSettings thresholds = thresholdSettingsService.getThresholdSettings(server);

        // Calculate player's current status
        PlayerStatusCalculator.PlayerStatus currentStatus = statusCalculator.calculateStatus(server, player.getPunishments());

        // Determine offense level based on current points and punishment category
        String category = punishmentType.getCategory();
        boolean isSocial = punishmentType.isSocial();
        int relevantPoints = isSocial ? currentStatus.socialPoints() : currentStatus.gameplayPoints();
        String offenseLevel = thresholds.getOffenseLevelInternal(relevantPoints, isSocial);

        // Calculate offender levels from points using configurable thresholds (separate for social/gameplay)
        String socialOffenderLevel = thresholds.getSocialOffenderLevel(currentStatus.socialPoints());
        String gameplayOffenderLevel = thresholds.getGameplayOffenderLevel(currentStatus.gameplayPoints());

        // Build preview response
        PunishmentPreviewResponse.PunishmentPreviewResponseBuilder builder = PunishmentPreviewResponse.builder()
                .status(200)
                .success(true)
                .socialStatus(socialOffenderLevel)
                .gameplayStatus(gameplayOffenderLevel)
                .socialPoints(currentStatus.socialPoints())
                .gameplayPoints(currentStatus.gameplayPoints())
                .offenseLevel(offenseLevel)
                .singleSeverityPunishment(punishmentType.isSingleSeverityPunishment())
                .permanentUntilUsernameChange(punishmentType.isPermanentUntilUsernameChange())
                .permanentUntilSkinChange(punishmentType.isPermanentUntilSkinChange())
                .canBeAltBlocking(punishmentType.isCanBeAltBlocking())
                .canBeStatWiping(punishmentType.isCanBeStatWiping())
                .category(category);

        if (punishmentType.isSingleSeverityPunishment() ||
            punishmentType.isPermanentUntilUsernameChange() ||
            punishmentType.isPermanentUntilSkinChange()) {
            // Single severity punishment
            builder.singleSeverity(buildSeverityPreview(punishmentType, "regular", offenseLevel,
                    currentStatus, punishmentType.isSocial(), thresholds));
        } else {
            // Multi-severity punishment
            builder.lenient(buildSeverityPreview(punishmentType, "low", offenseLevel,
                    currentStatus, punishmentType.isSocial(), thresholds));
            builder.regular(buildSeverityPreview(punishmentType, "regular", offenseLevel,
                    currentStatus, punishmentType.isSocial(), thresholds));
            builder.aggravated(buildSeverityPreview(punishmentType, "severe", offenseLevel,
                    currentStatus, punishmentType.isSocial(), thresholds));
        }

        return ResponseEntity.ok(builder.build());
    }

    private PunishmentPreviewResponse.SeverityPreview buildSeverityPreview(
            PunishmentType type, String severity, String offenseLevel,
            PlayerStatusCalculator.PlayerStatus currentStatus, boolean isSocial,
            OffenderThresholdSettings thresholds) {

        int points = type.getPointsForSeverity(severity);
        DurationDetail durationDetail = type.getDurationDetail(severity, offenseLevel);

        // If no duration detail or points from stored type, fall back to defaults
        PunishmentType defaultType = null;
        if (durationDetail == null || points == 0) {
            defaultType = DefaultPunishmentTypes.getAll().stream()
                    .filter(t -> t.getOrdinal() == type.getOrdinal())
                    .findFirst()
                    .orElse(null);
        }
        if (durationDetail == null && defaultType != null) {
            durationDetail = defaultType.getDurationDetail(severity, offenseLevel);
        }
        if (points == 0 && defaultType != null) {
            points = defaultType.getPointsForSeverity(severity);
        }

        long durationMs = durationDetail != null ? durationDetail.toMilliseconds() : 0L;
        boolean isPermanent = durationDetail != null && durationDetail.isPermanent();
        String punishmentResultType = durationDetail != null ?
                (durationDetail.isBan() ? "ban" : (durationDetail.isMute() ? "mute" : "kick")) : "unknown";

        // Calculate new points after this punishment
        int newSocialPoints = currentStatus.socialPoints() + (isSocial ? points : 0);
        int newGameplayPoints = currentStatus.gameplayPoints() + (isSocial ? 0 : points);

        return PunishmentPreviewResponse.SeverityPreview.builder()
                .severity(severity)
                .points(points)
                .durationMs(durationMs)
                .durationFormatted(formatDuration(durationMs, isPermanent))
                .punishmentType(punishmentResultType)
                .permanent(isPermanent)
                .newSocialStatus(thresholds.getSocialOffenderLevel(newSocialPoints))
                .newGameplayStatus(thresholds.getGameplayOffenderLevel(newGameplayPoints))
                .newSocialPoints(newSocialPoints)
                .newGameplayPoints(newGameplayPoints)
                .build();
    }

    private String formatDuration(long durationMs, boolean isPermanent) {
        if (isPermanent || durationMs < 0) {
            return "Permanent";
        }
        if (durationMs == 0) {
            return "Instant";
        }

        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;

        if (months > 0) {
            return months + (months == 1 ? " month" : " months");
        } else if (weeks > 0) {
            return weeks + (weeks == 1 ? " week" : " weeks");
        } else if (days > 0) {
            return days + (days == 1 ? " day" : " days");
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        } else if (minutes > 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        } else {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgePunishment(
            @RequestBody AcknowledgeRequest request,
            HttpServletRequest httpRequest
    ) {
        Server server = RequestUtil.getRequestServer(httpRequest);
        MongoTemplate template = mongoProvider.getFromDatabaseName(server.getDatabaseName());

        // Find player by UUID (same approach as sync - this is reliable)
        Query findQuery = Query.query(Criteria.where("minecraftUuid").is(request.playerUuid()));
        Player player = template.findOne(findQuery, Player.class, CollectionName.PLAYERS);

        if (player == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Player not found: " + request.playerUuid()
            ));
        }

        // Verify the punishment exists in the player's punishments
        Punishment targetPunishment = player.getPunishments().stream()
                .filter(p -> p.getId().equals(request.punishmentId()))
                .findFirst()
                .orElse(null);

        if (targetPunishment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found: " + request.punishmentId() + " for player: " + request.playerUuid()
            ));
        }

        // If already started, no need to update again
        if (targetPunishment.getStarted() != null) {
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", "Punishment already acknowledged"
            ));
        }

        // Update the punishment in-memory and save the whole player
        // This is less efficient but avoids MongoDB array query issues
        targetPunishment.setStarted(new Date());
        template.save(player, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Punishment acknowledged"
        ));
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

        // Find the specific punishment and check if it's active
        Punishment punishment = player.getPunishments().stream()
                .filter(p -> punishmentId.equals(p.getId()))
                .findFirst()
                .orElse(null);

        if (punishment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", 404,
                    "message", "Punishment not found"
            ));
        }

        // Check if the punishment is already inactive/pardoned
        if (!statusCalculator.isPunishmentActive(punishment)) {
            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "success", false,
                    "pardonedCount", 0,
                    "message", "Punishment is already inactive or pardoned"
            ));
        }

        Date now = new Date();

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_PARDON",
                now,
                request.issuerName(),
                request.reason() != null ? request.reason() : "",
                null,
                null,
                null
        );

        // Create automatic note for pardon
        PunishmentNote pardonNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "pardoned punishment",
                now,
                request.issuerName()
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .push("punishments.$.notes", pardonNote)
                .set("punishments.$.data.active", false);

        // Add separate note for reason if provided
        if (request.reason() != null && !request.reason().isBlank()) {
            PunishmentNote reasonNote = new PunishmentNote(
                    new ObjectId().toHexString(),
                    request.reason(),
                    now,
                    request.issuerName()
            );
            update.push("punishments.$.notes", reasonNote);
        }

        template.updateFirst(updateQuery, update, Player.class, CollectionName.PLAYERS);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "success", true,
                "pardonedCount", 1,
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

        Date now = new Date();

        PunishmentEvidence evidence = new PunishmentEvidence(
                null,
                request.evidenceUrl(),
                "url",
                request.issuerName(),
                now,
                null, null, null
        );

        // Create automatic note for evidence addition
        PunishmentNote evidenceNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "added evidence",
                now,
                request.issuerName()
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .push("punishments.$.evidence", evidence)
                .push("punishments.$.notes", evidenceNote);
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

        Date now = new Date();

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "MANUAL_DURATION_CHANGE",
                now,
                request.issuerName(),
                "Duration changed",
                request.newDuration(),
                null,
                null
        );

        // Create automatic note for duration change
        String durationText = request.newDuration() == null || request.newDuration() < 0 ? "permanent" : formatDuration(request.newDuration(), false);
        PunishmentNote durationNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "changed duration to " + durationText,
                now,
                request.issuerName()
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .push("punishments.$.notes", durationNote)
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

        // Create automatic note for option toggle
        String optionDisplayName = switch (request.option()) {
            case "ALT_BLOCKING" -> "alt-blocking";
            case "STAT_WIPE" -> "stat wipe";
            default -> request.option().toLowerCase();
        };
        PunishmentNote toggleNote = new PunishmentNote(
                new ObjectId().toHexString(),
                (request.enabled() ? "enabled " : "disabled ") + optionDisplayName,
                new Date(),
                request.issuerName()
        );

        Query updateQuery = Query.query(
                Criteria.where("minecraftUuid").is(player.getMinecraftUuid().toString())
                        .and("punishments.id").is(punishmentId)
        );

        Update update = new Update()
                .set(fieldName, request.enabled())
                .push("punishments.$.notes", toggleNote);
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

        // Calculate duration from severity if not explicitly provided
        Long calculatedDuration = request.duration();
        if (calculatedDuration == null && request.severity() != null) {
            // Get punishment type and calculate duration based on severity and offense level
            List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
            PunishmentType punishmentType = types.stream()
                    .filter(t -> t.getOrdinal() == request.typeOrdinal())
                    .findFirst()
                    .orElse(null);

            if (punishmentType != null) {
                // Calculate player's current offense level
                OffenderThresholdSettings thresholds = thresholdSettingsService.getThresholdSettings(server);
                PlayerStatusCalculator.PlayerStatus currentStatus = statusCalculator.calculateStatus(server, player.getPunishments());

                boolean isSocial = punishmentType.isSocial();
                int relevantPoints = isSocial ? currentStatus.socialPoints() : currentStatus.gameplayPoints();
                String offenseLevel = thresholds.getOffenseLevelInternal(relevantPoints, isSocial);

                // Map severity string to internal severity used by getDurationDetail
                String internalSeverity = switch (request.severity().toLowerCase()) {
                    case "lenient" -> "low";
                    case "regular" -> "regular";
                    case "aggravated", "severe" -> "severe";
                    default -> "regular";
                };

                DurationDetail durationDetail = punishmentType.getDurationDetail(internalSeverity, offenseLevel);

                // If no duration detail from stored type, fall back to defaults
                if (durationDetail == null) {
                    PunishmentType defaultType = DefaultPunishmentTypes.getAll().stream()
                            .filter(t -> t.getOrdinal() == request.typeOrdinal())
                            .findFirst()
                            .orElse(null);
                    if (defaultType != null) {
                        durationDetail = defaultType.getDurationDetail(internalSeverity, offenseLevel);
                    }
                }

                if (durationDetail != null) {
                    long durationMs = durationDetail.toMilliseconds();
                    // toMilliseconds returns -1 for permanent, positive for timed
                    // Don't accept 0 as valid (would be treated as permanent anyway)
                    if (durationMs != 0) {
                        calculatedDuration = durationMs;
                    }
                }
            }
        }

        // Only store duration if we have a valid value
        // -1 = permanent, positive = timed duration in ms
        if (calculatedDuration != null && calculatedDuration != 0) {
            data.put("duration", calculatedDuration);
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            data.put("reason", request.reason());
        }
        data.putIfAbsent("active", true);

        List<PunishmentNote> notes = new ArrayList<>();
        // Add automatic "issued" note
        notes.add(new PunishmentNote(
                new ObjectId().toHexString(),
                "issued punishment",
                now,
                request.issuerName()
        ));
        // Add reason as a separate note if provided
        if (request.reason() != null && !request.reason().isBlank()) {
            notes.add(new PunishmentNote(
                    new ObjectId().toHexString(),
                    request.reason(),
                    now,
                    request.issuerName()
            ));
        }
        // Add any additional notes from the request
        if (request.notes() != null) {
            for (String noteText : request.notes()) {
                notes.add(new PunishmentNote(new ObjectId().toHexString(), noteText, now, request.issuerName()));
            }
        }

        String punishmentId = IdGenerator.generatePunishmentId();

        Punishment punishment = new Punishment(
                punishmentId,
                request.typeOrdinal(),
                request.issuerName(),
                now,
                null,
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
