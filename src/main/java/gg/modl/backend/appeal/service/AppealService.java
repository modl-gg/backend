package gg.modl.backend.appeal.service;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.CreateAppealRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.util.IdGenerator;
import gg.modl.backend.util.PlayerDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {
    private final TicketMongoRepository ticketRepository;
    private final PlayerMongoRepository playerRepository;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final IdGenerator idGenerator;

    private static final String APPEAL_TYPE = TicketCategory.APPEAL.getId();

    public List<TicketResponse> getAppealsByPunishment(Server server, String punishmentId) {
        return ticketRepository.findAppealsByPunishmentId(server, punishmentId)
                .stream().map(this::toTicketResponse).toList();
    }

    public Optional<TicketResponse> getAppealById(Server server, String appealId) {
        return ticketRepository.findByTicketId(server, appealId)
                .filter(t -> t.getType() == TicketCategory.APPEAL)
                .map(this::toTicketResponse);
    }

    public TicketResponse createAppeal(Server server, CreateAppealRequest request) {
        Player player = findPlayerWithPunishment(server, request.playerUuid(), request.punishmentId());
        if (player == null) {
            throw new IllegalArgumentException("Punishment not found for the specified player");
        }

        Punishment punishment = player.getPunishments().stream()
                .filter(p -> p.getId().equals(request.punishmentId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Punishment details not found"));

        if (ticketRepository.existsAppealForPunishment(server, request.punishmentId())) {
            throw new IllegalStateException("An appeal already exists for this punishment");
        }

        String appealId = generateAppealId(server);

        Map<String, Object> data = new HashMap<>();
        data.put("punishmentId", request.punishmentId());
        data.put("playerUuid", request.playerUuid());
        data.put("contactEmail", request.email());

        if (request.additionalData() != null) {
            data.putAll(request.additionalData());
        }

        String username = PlayerDataUtils.extractLatestUsername(player.getUsernames());

        String initialContent = buildInitialContent(request);

        TicketReply initialReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(username)
                .content(initialContent)
                .type("player")
                .created(new Date())
                .staff(false)
                .attachments(request.attachments() != null ? request.attachments() : new ArrayList<>())
                .build();

        Ticket appeal = Ticket.builder()
                .id(appealId)
                .type(TicketCategory.APPEAL)
                .status(TicketStatus.OPEN)
                .appealWorkflowStatus(AppealWorkflowStatus.OPEN)
                .subject("Appeal for Punishment: " + request.punishmentId())
                .tags(new ArrayList<>())
                .creatorName(username)
                .creatorUuid(request.playerUuid())
                .notes(new ArrayList<>())
                .replies(new ArrayList<>(List.of(initialReply)))
                .data(data)
                .locked(false)
                .created(new Date())
                .updatedAt(new Date())
                .build();

        ticketRepository.saveAppeal(server, appeal);

        linkAppealToPunishment(server, request.playerUuid(), request.punishmentId(), appealId);

        return toTicketResponse(appeal);
    }

    public Optional<TicketReply> addReply(Server server, String appealId, AddAppealReplyRequest request) {
        Optional<Ticket> appealOpt = ticketRepository.findByTicketId(server, appealId);

        if (appealOpt.isEmpty() || appealOpt.get().getType() != TicketCategory.APPEAL) {
            return Optional.empty();
        }

        Ticket appeal = appealOpt.get();

        if (appeal.isLocked()) {
            throw new IllegalStateException("Appeal is locked and cannot accept new replies");
        }

        TicketReply newReply = TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .content(request.content())
                .type(request.type())
                .created(new Date())
                .staff(request.staff())
                .action(request.action())
                .avatar(request.avatar())
                .attachments(request.attachments() != null ? request.attachments() : new ArrayList<>())
                .build();

        ticketRepository.pushReply(server, appealId, newReply);

        return Optional.of(newReply);
    }

    public Optional<TicketResponse> updateStatus(Server server, String appealId, UpdateAppealStatusRequest request) {
        Optional<Ticket> appealOpt = ticketRepository.findByTicketId(server, appealId);

        if (appealOpt.isEmpty() || appealOpt.get().getType() != TicketCategory.APPEAL) {
            return Optional.empty();
        }

        Ticket appeal = appealOpt.get();

        Update update = new Update().set("updatedAt", new Date());
        List<TicketReply> systemReplies = new ArrayList<>();
        boolean statusChanged = false;

        AppealWorkflowStatus requestedWorkflowStatus = null;
        if (request.status() != null) {
            requestedWorkflowStatus = AppealWorkflowStatus.fromCanonicalId(request.status());
        }

        if (requestedWorkflowStatus != null && requestedWorkflowStatus != appeal.getAppealWorkflowStatus()) {
            TicketStatus lifecycleStatus = requestedWorkflowStatus.isTerminal() ? TicketStatus.CLOSED : TicketStatus.OPEN;
            update.set("appealWorkflowStatus", requestedWorkflowStatus.getId());
            update.set("status", lifecycleStatus.getId());
            update.set("locked", lifecycleStatus.isTerminal());
            appeal.setAppealWorkflowStatus(requestedWorkflowStatus);
            appeal.setStatus(lifecycleStatus);
            appeal.setLocked(lifecycleStatus.isTerminal());
            statusChanged = true;

            systemReplies.add(createSystemReply(
                    request.staffUsername(),
                    "Appeal status changed to " + requestedWorkflowStatus.getDisplayName() + ".",
                    "APPEAL_STATUS_" + requestedWorkflowStatus.name()
            ));
        }

        if (request.resolution() != null) {
            Map<String, Object> data = appeal.getData() != null ? new HashMap<>(appeal.getData()) : new HashMap<>();
            if (!request.resolution().equals(data.get("resolution"))) {
                data.put("resolution", request.resolution());
                update.set("data", data);
                appeal.setData(data);

                systemReplies.add(createSystemReply(
                        request.staffUsername(),
                        "Resolution set to " + request.resolution() + ".",
                        "RESOLUTION_" + request.resolution().toUpperCase().replace(" ", "_")
                ));
            }
        }

        if (!statusChanged && request.locked() != null && request.locked() != appeal.isLocked()) {
            TicketStatus lifecycleStatus = request.locked() ? TicketStatus.CLOSED : TicketStatus.OPEN;
            update.set("locked", request.locked());
            update.set("status", lifecycleStatus.getId());
            appeal.setLocked(request.locked());
            appeal.setStatus(lifecycleStatus);

            systemReplies.add(createSystemReply(
                    request.staffUsername(),
                    "Ticket " + (request.locked() ? "locked" : "unlocked") + ".",
                    request.locked() ? "LOCKED" : "UNLOCKED"
            ));
        }

        for (TicketReply reply : systemReplies) {
            update.push("replies", reply);
            appeal.getReplies().add(reply);
        }

        ticketRepository.applyStatusUpdate(server, appealId, update);

        if (shouldPardonPunishment(appeal.getAppealWorkflowStatus())) {
            pardonPunishment(server, appeal, request.staffUsername());
        } else if (shouldRejectPunishment(appeal.getAppealWorkflowStatus())) {
            addAppealRejectedNote(server, appeal, request.staffUsername());
        }

        return Optional.of(toTicketResponse(appeal));
    }

    private boolean shouldRejectPunishment(AppealWorkflowStatus workflowStatus) {
        return workflowStatus == AppealWorkflowStatus.REJECTED;
    }

    private void addAppealRejectedNote(Server server, Ticket appeal, String staffUsername) {
        Map<String, Object> data = appeal.getData();
        if (data == null) return;

        String punishmentId = (String) data.get("punishmentId");
        String playerUuid = (String) data.get("playerUuid");

        if (punishmentId == null || playerUuid == null) return;

        Query playerQuery = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );

        Date now = new Date();
        String staffName = staffUsername != null ? staffUsername : "System";

        PunishmentNote appealRejectedNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "rejected appeal (#" + appeal.getId() + ")",
                now,
                staffName,
                null
        );

        Update update = new Update()
                .push("punishments.$.notes", appealRejectedNote)
                .set("punishments.$.data.appealOutcome", "Rejected")
                .set("punishments.$.data.appealTicketId", appeal.getId());

        playerRepository.updateFirst(server, playerQuery, update);
    }

    private boolean shouldPardonPunishment(AppealWorkflowStatus workflowStatus) {
        return workflowStatus == AppealWorkflowStatus.APPROVED;
    }

    private void pardonPunishment(Server server, Ticket appeal, String staffUsername) {
        Map<String, Object> data = appeal.getData();
        if (data == null) return;

        String punishmentId = (String) data.get("punishmentId");
        String playerUuid = (String) data.get("playerUuid");

        if (punishmentId == null || playerUuid == null) return;

        Query playerQuery = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );

        Date now = new Date();
        String staffName = staffUsername != null ? staffUsername : "System";

        PunishmentModification modification = new PunishmentModification(
                new ObjectId().toHexString(),
                "APPEAL_ACCEPT",
                now,
                staffName,
                null,
                "Appeal approved",
                null,
                appeal.getId(),
                null
        );

        PunishmentNote appealAcceptedNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "accepted appeal (#" + appeal.getId() + ")",
                now,
                staffName,
                null
        );

        Update update = new Update()
                .push("punishments.$.modifications", modification)
                .push("punishments.$.notes", appealAcceptedNote)
                .set("punishments.$.data.appealOutcome", "Approved")
                .set("punishments.$.data.appealTicketId", appeal.getId());

        playerRepository.updateFirst(server, playerQuery, update);

        playerRepository.findOne(server, Query.query(Criteria.where("minecraftUuid").is(playerUuid)))
                .ifPresent(player -> {
                    Punishment appealedPunishment = player.getPunishments().stream()
                            .filter(p -> punishmentId.equals(p.getId()))
                            .findFirst()
                            .orElse(null);
                    if (appealedPunishment != null && appealedPunishment.getData() != null
                            && Boolean.TRUE.equals(appealedPunishment.getData().get("altBlocking"))) {
                        punishmentLifecycleService.cascadePardonLinkedBans(server.getDatabaseName(), punishmentId);
                    }
                });
    }

    private TicketReply createSystemReply(String staffUsername, String content, String action) {
        return TicketReply.builder()
                .id(UUID.randomUUID().toString())
                .name(staffUsername != null ? staffUsername : "System")
                .content(content)
                .type("system")
                .created(new Date())
                .staff(true)
                .action(action)
                .build();
    }

    private String generateAppealId(Server server) {
        String appealId;
        int attempts = 0;

        do {
            int randomId = idGenerator.nextSixDigitInt();
            appealId = "APPEAL-" + randomId;
            attempts++;
        } while (ticketRepository.existsByTicketId(server, appealId) && attempts < 10);

        return appealId;
    }

    private Player findPlayerWithPunishment(Server server, String playerUuid, String punishmentId) {
        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );
        return playerRepository.findOne(server, query).orElse(null);
    }

    private void linkAppealToPunishment(Server server, String playerUuid, String punishmentId, String appealId) {
        Query query = Query.query(
                Criteria.where("minecraftUuid").is(playerUuid)
                        .and("punishments.id").is(punishmentId)
        );

        PunishmentNote appealOpenedNote = new PunishmentNote(
                new ObjectId().toHexString(),
                "opened appeal (#" + appealId + ")",
                new Date(),
                "System",
                null
        );

        Update update = new Update()
                .push("punishments.$.attachedTicketIds", appealId)
                .push("punishments.$.notes", appealOpenedNote);
        playerRepository.updateFirst(server, query, update);
    }

    private String buildInitialContent(CreateAppealRequest request) {
        StringBuilder content = new StringBuilder();

        if (request.reason() != null && !request.reason().isBlank()) {
            content.append("Appeal Reason: ").append(request.reason()).append("\n");
        }

        if (request.evidence() != null && !request.evidence().isBlank()) {
            content.append("Evidence: ").append(request.evidence()).append("\n");
        }

        if (request.additionalData() != null && !request.additionalData().isEmpty()) {
            content.append("\nAdditional Information:\n");
            for (Map.Entry<String, Object> entry : request.additionalData().entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    String fieldLabel = request.fieldLabels() != null && request.fieldLabels().containsKey(entry.getKey())
                            ? request.fieldLabels().get(entry.getKey())
                            : formatFieldLabel(entry.getKey());

                    if (value instanceof List<?> list) {
                        if (!list.isEmpty()) {
                            content.append(fieldLabel).append(":\n");
                            for (Object item : list) {
                                content.append("  - ").append(item).append("\n");
                            }
                        }
                    } else if (value instanceof Boolean bool) {
                        content.append(fieldLabel).append(": ").append(bool ? "Yes" : "No").append("\n");
                    } else {
                        content.append(fieldLabel).append(": ").append(value).append("\n");
                    }
                }
            }
        }

        content.append("\nContact Email: ").append(request.email());

        if (content.toString().trim().equals("Contact Email: " + request.email())) {
            return "Appeal submitted for punishment " + request.punishmentId() + ".\n\nContact Email: " + request.email();
        }

        return content.toString();
    }

    private String formatFieldLabel(String key) {
        return key.replaceAll("([A-Z])", " $1")
                .replaceFirst("^.", String.valueOf(Character.toUpperCase(key.charAt(0))));
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                TicketCategory.APPEAL.getId(),
                TicketCategory.APPEAL.getDisplayName(),
                ticket.getSubject() != null ? ticket.getSubject() : "No Subject",
                ticket.getStatus() != null ? ticket.getStatus().getId() : TicketStatus.OPEN.getId(),
                ticket.getAppealWorkflowStatus() != null ? ticket.getAppealWorkflowStatus().getId() : AppealWorkflowStatus.OPEN.getId(),
                ticket.getCreatorName(),
                ticket.getCreatorUuid(),
                ticket.getCreatorName(),
                ticket.getReportedPlayer(),
                ticket.getReportedPlayerUuid(),
                ticket.getCreated(),
                ticket.isLocked(),
                ticket.getReplies(),
                ticket.getNotes(),
                ticket.getTags(),
                ticket.getFormData(),
                ticket.getData(),
                ticket.getChatMessages(),
                ticket.getAiAnalysis(),
                ticket.isEmailAuthEnabled(),
                ticket.isHidden()
        );
    }
}
