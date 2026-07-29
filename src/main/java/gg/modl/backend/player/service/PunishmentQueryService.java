package gg.modl.backend.player.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.PunishmentMongoRepository;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentEvidence;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentDataView;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.data.punishment.PunishmentStatus;
import gg.modl.backend.player.dto.response.PunishmentPreviewResponse;
import gg.modl.backend.player.dto.response.PunishmentPreviewView;
import gg.modl.backend.player.dto.response.PunishmentResponse;
import gg.modl.backend.player.dto.response.PunishmentSearchResult;
import gg.modl.backend.player.dto.response.AppealEligibility;
import gg.modl.backend.player.dto.response.AppealInfoView;
import gg.modl.backend.player.dto.response.LinkedBanView;
import gg.modl.backend.player.dto.response.PunishmentView;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.DefaultPunishmentTypes;
import gg.modl.backend.settings.data.DurationDetail;
import gg.modl.backend.settings.data.OffenderThresholdSettings;
import gg.modl.backend.settings.data.PunishmentDurationResolver;
import gg.modl.backend.settings.data.PunishmentType;
import gg.modl.backend.settings.service.OffenderThresholdSettingsService;
import gg.modl.backend.settings.service.PunishmentTypeIndex;
import gg.modl.backend.settings.service.PunishmentTypeService;
import gg.modl.backend.infrastructure.util.UuidUtils;
import gg.modl.backend.storage.service.EvidenceUploadTokenService;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PunishmentQueryService {
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMongoRepository punishmentRepository;
    private final PlayerStatusCalculator statusCalculator;
    private final PunishmentTypeService punishmentTypeService;
    private final OffenderThresholdSettingsService thresholdSettingsService;
    private final EvidenceUploadTokenService evidenceUploadTokenService;
    private final IssuerNameResolver issuerNameResolver;
    private final TicketMongoRepository ticketRepository;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RECENT_PUNISHMENT_SCAN_LIMIT = 500;

    public List<PunishmentResponse> getActivePunishments(Server server, UUID playerUuid) {
        Player player = playerRepository.findByMinecraftUuid(server, playerUuid.toString()).orElse(null);
        if (player == null) {
            return new ArrayList<>();
        }

        List<Punishment> activePunishments = player.getPunishments()
            .stream()
            .filter(statusCalculator::isPunishmentActive)
            .toList();
        return toPunishmentResponses(server, activePunishments);
    }

    public List<PunishmentResponse> getPlayerPunishmentResponses(Server server, Player player) {
        return toPunishmentResponses(server, player.getPunishments());
    }

    private List<PunishmentResponse> toPunishmentResponses(Server server, List<Punishment> punishments) {
        Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPunishments(server, punishments);
        return punishments.stream()
            .map(punishment -> buildPunishmentResponse(server, punishment, null, resolvedIssuers))
            .toList();
    }

    private PunishmentResponse toPunishmentResponseWithPlayer(Server server, Punishment punishment, Player player) {
        return buildPunishmentResponse(server, punishment, player, issuerNameResolver.resolveForPunishments(server, List.of(punishment)));
    }

    private PunishmentResponse buildPunishmentResponse(Server server, Punishment punishment, Player player, Map<String, String> resolvedIssuers) {
        PunishmentDataView data = punishment.data();
        boolean active = statusCalculator.isPunishmentActive(punishment);
        Date expires = statusCalculator.getEffectiveExpiry(punishment);

        String playerUuid = player != null ? player.getMinecraftUuid().toString() : null;
        String playerUsername = player != null ? PlayerDataUtils.extractLatestUsername(player.getUsernames()) : null;

        int ordinal = punishment.getTypeOrdinal();

        PunishmentType punishmentType = punishmentTypeService.getPunishmentTypeByOrdinal(server, ordinal).orElse(null);
        String effectiveCategory = statusCalculator.getEffectiveCategory(punishmentType, data);

        return new PunishmentResponse(
            punishment.getId(),
            punishmentTypeService.getPunishmentTypeName(server, ordinal),
            ordinal,
            PunishmentMapper.resolveIssuer(punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers),
            punishment.getIssued(),
            punishment.getStarted(),
            punishmentTypeService.isAppealable(server, ordinal),
            data.reason(),
            data.severity(),
            data.asMap() != null ? resolveOffenderStatus(data) : null,
            active,
            expires,
            playerUuid,
            playerUsername,
            data.asMap() != null ? data.altBlocking() : null,
            data.asMap() != null ? data.wipeAfterExpiry() : null,
            effectiveCategory,
            resolveModifications(punishment.getModifications(), resolvedIssuers),
            resolveNotes(punishment.getNotes(), resolvedIssuers),
            resolveEvidence(punishment.getEvidence(), resolvedIssuers),
            punishment.getAttachedTicketIds()
        );
    }

    private static String resolveOffenderStatus(PunishmentDataView data) {
        String status = data.status();
        if (status != null
            && !PunishmentStatus.UNSTARTED.equals(status)
            && !PunishmentStatus.PARDONED.equals(status)) {
            return status;
        }
        String offenseLevel = data.offenseLevel();
        if (offenseLevel != null) {
            return switch (offenseLevel.toLowerCase(Locale.ROOT)) {
                case "first" -> "low";
                default -> offenseLevel;
            };
        }
        return null;
    }

    static Set<String> collectIssuerIds(Punishment punishment) {
        Set<String> ids = new HashSet<>();
        if (punishment.getIssuerId() != null) {
            ids.add(punishment.getIssuerId());
        }
        for (PunishmentModification m : punishment.getModifications()) {
            if (m.issuerId() != null) {
                ids.add(m.issuerId());
            }
        }
        for (PunishmentNote n : punishment.getNotes()) {
            if (n.issuerId() != null) {
                ids.add(n.issuerId());
            }
        }
        for (PunishmentEvidence e : punishment.getEvidence()) {
            if (e.uploadedById() != null) {
                ids.add(e.uploadedById());
            }
        }
        return ids;
    }

    private static List<PunishmentModification> resolveModifications(List<PunishmentModification> modifications, Map<String, String> resolvedIssuers) {
        return modifications.stream().map(m -> new PunishmentModification(
            m.id(), m.type(), m.date(),
            PunishmentMapper.resolveIssuer(m.issuerId(), m.issuerName(), resolvedIssuers),
            m.issuerId(), m.reason(), m.effectiveDuration(), m.appealTicketId(), m.data()
        )).toList();
    }

    private static List<PunishmentNote> resolveNotes(List<PunishmentNote> notes, Map<String, String> resolvedIssuers) {
        return notes.stream().map(n -> new PunishmentNote(
            n.id(), n.text(), n.date(),
            PunishmentMapper.resolveIssuer(n.issuerId(), n.issuerName(), resolvedIssuers),
            n.issuerId()
        )).toList();
    }

    private static List<PunishmentEvidence> resolveEvidence(List<PunishmentEvidence> evidence, Map<String, String> resolvedIssuers) {
        return evidence.stream().map(e -> new PunishmentEvidence(
            e.text(), e.url(), e.type(),
            PunishmentMapper.resolveIssuer(e.uploadedById(), e.uploadedBy(), resolvedIssuers),
            e.uploadedById(), e.uploadedAt(), e.fileName(), e.fileType(), e.fileSize()
        )).toList();
    }

    public Optional<PunishmentView> getMinecraftPunishmentById(Server server, String punishmentId) {
        return findPunishmentContext(server, punishmentId).map(context -> {
            List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
            Map<String, String> resolvedIssuers = issuerNameResolver.resolveForPunishments(server, List.of(context.punishment()));
            return PunishmentMapper.toPunishmentView(context.punishment(), types, resolvedIssuers)
                .withPlayer(context.player().getMinecraftUuid().toString(), getLatestUsername(context.player()));
        });
    }

    public Optional<PunishmentContext> findPunishmentContext(Server server, String punishmentId) {
        return punishmentRepository.findByPunishmentId(server, punishmentId)
            .flatMap(player -> player.getPunishments()
                .stream()
                .filter(punishment -> punishmentId.equals(punishment.getId()))
                .findFirst()
                .map(punishment -> new PunishmentContext(player, punishment)));
    }

    private String getLatestUsername(Player player) {
        return PlayerDataUtils.extractLatestUsername(player.getUsernames());
    }

    public List<PunishmentSearchResult> searchPunishments(Server server, String searchQuery, boolean activeOnly) {
        List<PunishmentSearchResult> results = new ArrayList<>();

        Pattern pattern = Pattern.compile(Pattern.quote(searchQuery), Pattern.CASE_INSENSITIVE);
        List<Player> players = punishmentRepository.findPlayersWithPunishments(server, 50);

        Set<String> allIssuerIds = new HashSet<>();
        for (Player player : players) {
            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getIssuerId() != null) {
                    allIssuerIds.add(punishment.getIssuerId());
                }
            }
        }
        Map<String, String> resolvedIssuers = allIssuerIds.isEmpty()
                                              ? Map.of()
                                              : issuerNameResolver.batchResolve(allIssuerIds, server);

        for (Player player : players) {
            String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

            for (Punishment punishment : player.getPunishments()) {
                if (activeOnly && !statusCalculator.isPunishmentActive(punishment)) {
                    continue;
                }

                String resolvedIssuerName = PunishmentMapper.resolveIssuer(
                    punishment.getIssuerId(), punishment.getIssuerName(), resolvedIssuers);

                boolean matches = punishment.getId().contains(searchQuery) ||
                                  pattern.matcher(resolvedIssuerName).find();

                String reason = punishment.data().reason();
                if (reason != null && pattern.matcher(reason).find()) {
                    matches = true;
                }

                if (matches) {
                    results.add(new PunishmentSearchResult(
                        punishment.getId(),
                        username,
                        punishment.getTypeOrdinal(),
                        statusCalculator.isPunishmentActive(punishment) ? "Active" : "Inactive",
                        punishment.getIssued()
                    ));

                    if (results.size() >= 20) {
                        return results;
                    }
                }
            }
        }

        return results;
    }

    public List<PunishmentView> getRecentPunishments(Server server, int hours) {
        Date cutoff = new Date(System.currentTimeMillis() - (hours * 60L * 60L * 1000L));
        List<Player> players = punishmentRepository.findWithPunishmentsIssuedAfter(server, cutoff, RECENT_PUNISHMENT_SCAN_LIMIT);
        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);

        List<PunishmentContext> recent = new ArrayList<>();
        Set<String> issuerIds = new HashSet<>();
        for (Player player : players) {
            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getIssued() != null && punishment.getIssued().after(cutoff)) {
                    recent.add(new PunishmentContext(player, punishment));
                    issuerIds.addAll(collectIssuerIds(punishment));
                }
            }
        }
        Map<String, String> resolvedIssuers = issuerIds.isEmpty() ? Map.of() : issuerNameResolver.batchResolve(issuerIds, server);

        List<PunishmentView> punishments = new ArrayList<>();
        for (PunishmentContext entry : recent) {
            punishments.add(PunishmentMapper.toPunishmentView(entry.punishment(), types, resolvedIssuers)
                .withPlayer(entry.player().getMinecraftUuid().toString(), getLatestUsername(entry.player())));
        }

        punishments.sort((left, right) -> right.issued().compareTo(left.issued()));
        return punishments.size() > 100 ? punishments.subList(0, 100) : punishments;
    }

    public List<LinkedBanView> getLinkedBansForParent(Server server, String parentPunishmentId) {
        List<LinkedBanView> results = new ArrayList<>();
        List<Player> players = punishmentRepository.findByLinkedBanId(server, parentPunishmentId);

        for (Player player : players) {
            String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

            for (Punishment punishment : player.getPunishments()) {
                if (punishment.getTypeOrdinal() == Punishment.LINKED_BAN_TYPE_ORDINAL &&
                    parentPunishmentId.equals(punishment.data().linkedBanId())) {

                    results.add(new LinkedBanView(
                        punishment.getId(),
                        player.getMinecraftUuid().toString(),
                        username,
                        statusCalculator.isPunishmentActive(punishment)
                    ));
                }
            }
        }

        return results;
    }

    public PunishmentPreviewView previewPunishment(Server server, String playerUuid, int typeOrdinal) {
        Player player = playerRepository.findByMinecraftUuid(server, UuidUtils.normalize(playerUuid)).orElse(null);
        if (player == null) {
            return PunishmentPreviewResponse.error("Player not found");
        }

        List<PunishmentType> types = punishmentTypeService.getPunishmentTypes(server);
        PunishmentType punishmentType = PunishmentTypeIndex.byOrdinal(types).get(typeOrdinal);
        if (punishmentType == null) {
            return PunishmentPreviewResponse.error("Punishment type not found");
        }

        OffenderThresholdSettings thresholds = thresholdSettingsService.getThresholdSettings(server);
        PlayerStatusCalculator.PlayerStatus currentStatus = statusCalculator.calculateStatus(server, player.getPunishments());

        boolean isSocial = punishmentType.isSocial();
        int relevantPoints = isSocial ? currentStatus.socialPoints() : currentStatus.gameplayPoints();
        String offenseLevel = thresholds.getOffenseLevelInternal(relevantPoints, isSocial);
        String socialOffenderLevel = thresholds.getSocialOffenderLevel(currentStatus.socialPoints());
        String gameplayOffenderLevel = thresholds.getGameplayOffenderLevel(currentStatus.gameplayPoints());

        String displayStatus = PunishmentMapper.offenseDisplayStatus(offenseLevel);

        PunishmentPreviewResponse.PunishmentPreviewResponseBuilder builder = PunishmentPreviewResponse.builder()
            .status(200)
            .success(true)
            .offenderStatus(displayStatus)
            .socialStatus(socialOffenderLevel)
            .gameplayStatus(gameplayOffenderLevel)
            .socialPoints(currentStatus.socialPoints())
            .gameplayPoints(currentStatus.gameplayPoints())
            .singleSeverityPunishment(punishmentType.isSingleSeverityPunishment())
            .permanentUntilUsernameChange(punishmentType.isPermanentUntilUsernameChange())
            .permanentUntilSkinChange(punishmentType.isPermanentUntilSkinChange())
            .canBeAltBlocking(punishmentType.isCanBeAltBlocking())
            .canBeStatWiping(punishmentType.isCanBeStatWiping())
            .category(punishmentType.getCategory());

        if (punishmentType.isSingleSeverityPunishment()
            || punishmentType.isPermanentUntilUsernameChange()
            || punishmentType.isPermanentUntilSkinChange()) {
            builder.singleSeverity(buildSeverityPreview(punishmentType, "regular", offenseLevel, currentStatus, isSocial, thresholds));
        } else {
            builder.lenient(buildSeverityPreview(punishmentType, "low", offenseLevel, currentStatus, isSocial, thresholds));
            builder.regular(buildSeverityPreview(punishmentType, "regular", offenseLevel, currentStatus, isSocial, thresholds));
            builder.aggravated(buildSeverityPreview(punishmentType, "severe", offenseLevel, currentStatus, isSocial, thresholds));
        }

        return builder.build();
    }

    private PunishmentPreviewResponse.SeverityPreview buildSeverityPreview(
        PunishmentType type,
        String severity,
        String offenseLevel,
        PlayerStatusCalculator.PlayerStatus currentStatus,
        boolean isSocial,
        OffenderThresholdSettings thresholds
    ) {
        int points = type.getPointsForSeverity(severity);
        DurationDetail durationDetail = PunishmentDurationResolver.resolveDetail(type, severity, offenseLevel);

        if (points == 0) {
            PunishmentType defaultType = PunishmentTypeIndex.byOrdinal(DefaultPunishmentTypes.getAll()).get(type.getOrdinal());
            if (defaultType != null) {
                points = defaultType.getPointsForSeverity(severity);
            }
        }

        long durationMs = durationDetail != null ? durationDetail.toMilliseconds() : 0L;
        boolean permanent = durationDetail != null && durationDetail.isPermanent();
        String punishmentType = durationDetail != null
                                ? (durationDetail.isBan() ? "ban" : (durationDetail.isMute() ? "mute" : "kick"))
                                : "unknown";

        int newSocialPoints = currentStatus.socialPoints() + (isSocial ? points : 0);
        int newGameplayPoints = currentStatus.gameplayPoints() + (isSocial ? 0 : points);

        return PunishmentPreviewResponse.SeverityPreview.builder()
            .severity(severity)
            .points(points)
            .durationMs(durationMs)
            .durationFormatted(PunishmentMapper.formatDuration(durationMs, permanent))
            .punishmentType(punishmentType)
            .permanent(permanent)
            .newSocialStatus(thresholds.getSocialOffenderLevel(newSocialPoints))
            .newGameplayStatus(thresholds.getGameplayOffenderLevel(newGameplayPoints))
            .newSocialPoints(newSocialPoints)
            .newGameplayPoints(newGameplayPoints)
            .build();
    }

    public Optional<String> createEvidenceUploadToken(Server server, String punishmentId, String issuerName) {
        return findPunishmentContext(server, punishmentId)
            .map(context -> evidenceUploadTokenService.createToken(
                server,
                punishmentId,
                context.player().getMinecraftUuid().toString(),
                issuerName != null ? issuerName : "Unknown"
            ));
    }

    public Optional<AppealEligibility> getPublicPunishmentWithAppealEligibility(Server server, String punishmentId) {
        PunishmentContext context = findPunishmentContext(server, punishmentId).orElse(null);
        if (context == null) {
            return Optional.empty();
        }

        PunishmentResponse punishment = toPunishmentResponseWithPlayer(server, context.punishment(), context.player());

        if (punishment.started() == null) {
            return Optional.of(new AppealEligibility.NotStarted(
                "This punishment has not been started yet and cannot be appealed at this time."));
        }

        Map<String, Object> existingAppeal = null;
        List<Ticket> existingAppeals = ticketRepository.findAppealsByPunishmentId(server, punishmentId);
        if (!existingAppeals.isEmpty()) {
            Ticket latestAppeal = existingAppeals.stream()
                .max((left, right) -> {
                    Date leftDate = left.getCreated();
                    Date rightDate = right.getCreated();
                    if (leftDate == null && rightDate == null) {
                        return 0;
                    }
                    if (leftDate == null) {
                        return -1;
                    }
                    if (rightDate == null) {
                        return 1;
                    }
                    return leftDate.compareTo(rightDate);
                })
                .orElse(existingAppeals.get(0));
            existingAppeal = new HashMap<>();
            existingAppeal.put("id", latestAppeal.getId());
            existingAppeal.put("submittedDate", latestAppeal.getCreated());
            existingAppeal.put("status", latestAppeal.getStatus() != null ? latestAppeal.getStatus().getId() : TicketStatus.OPEN.getId());
            if (latestAppeal.getAppealWorkflowStatus() != null) {
                existingAppeal.put("appealWorkflowStatus", latestAppeal.getAppealWorkflowStatus().getId());
            }
            existingAppeal.put("locked", latestAppeal.isLocked());
        }

        Map<String, Object> appealForm = punishmentTypeService.getPunishmentTypeByOrdinal(server, punishment.typeOrdinal())
            .map(PunishmentType::getAppealForm)
            .filter(form -> form.getFields() != null && !form.getFields().isEmpty())
            .map(form -> OBJECT_MAPPER.convertValue(form, new TypeReference<Map<String, Object>>() {}))
            .orElse(null);

        return Optional.of(new AppealEligibility.Eligible(new AppealInfoView(
            punishment.id(),
            punishment.type(),
            punishment.issued(),
            punishment.expires(),
            punishment.active(),
            punishment.isAppealable(),
            context.player().getMinecraftUuid().toString(),
            existingAppeal,
            appealForm
        )));
    }

    public PunishmentResponse getPunishmentById(Server server, String punishmentId) {
        PunishmentContext context = findPunishmentContext(server, punishmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Punishment not found"));
        return toPunishmentResponseWithPlayer(server, context.punishment(), context.player());
    }

    public enum PunishmentOperationStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_REQUEST,
        NO_OP
    }

    public static Punishment findPunishment(Player player, String punishmentId) {
        if (player.getPunishments().isEmpty()) {
            return null;
        }
        return player.getPunishments()
            .stream()
            .filter(punishment -> punishmentId.equals(punishment.getId()))
            .findFirst()
            .orElse(null);
    }

    public record PunishmentContext(Player player, Punishment punishment) {
    }

    public record UploadedEvidenceItem(String url, String fileName, String fileType, Long fileSize) {
    }

    public record PunishmentOperationResult(
        PunishmentOperationStatus status,
        String message,
        boolean success,
        int affectedCount
    ) {
    }
}
