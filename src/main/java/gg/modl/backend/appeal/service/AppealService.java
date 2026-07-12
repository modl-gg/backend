package gg.modl.backend.appeal.service;

import gg.modl.backend.appeal.dto.request.AddAppealReplyRequest;
import gg.modl.backend.appeal.dto.request.CreateAppealRequest;
import gg.modl.backend.appeal.dto.request.UpdateAppealStatusRequest;
import gg.modl.backend.infrastructure.util.MongoKeyUtils;
import gg.modl.backend.database.mongo.repository.PlayerMongoRepository;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ResourceNotFoundException;
import gg.modl.backend.database.mongo.repository.TicketMongoRepository;
import gg.modl.backend.player.data.Player;
import gg.modl.backend.player.data.punishment.Punishment;
import gg.modl.backend.player.data.punishment.PunishmentModification;
import gg.modl.backend.player.data.punishment.PunishmentModificationType;
import gg.modl.backend.player.data.punishment.PunishmentNote;
import gg.modl.backend.player.service.PunishmentLifecycleService;
import gg.modl.backend.player.service.PunishmentMutationService;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.AppealWorkflowStatus;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.TicketCategory;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.data.TicketStatus;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.AppealWorkflowTransitionService;
import gg.modl.backend.ticket.service.PublicAccessProperties;
import gg.modl.backend.ticket.service.TicketContentService;
import gg.modl.backend.ticket.service.TicketEmailVerificationService;
import gg.modl.backend.ticket.service.TicketIdGenerator;
import gg.modl.backend.player.service.PlayerDataUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.infrastructure.util.UuidUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {
    private final TicketMongoRepository ticketRepository;
    private final PlayerMongoRepository playerRepository;
    private final PunishmentMutationService punishmentMutationService;
    private final PunishmentLifecycleService punishmentLifecycleService;
    private final TicketIdGenerator ticketIdGenerator;
    private final AppealWorkflowTransitionService workflowTransitionService;
    private final TicketContentService contentService;
    private final TicketEmailVerificationService verificationService;
    private final PublicAccessProperties publicAccessProperties;

    private static final String APPEAL_TYPE = TicketCategory.APPEAL.getId();

    public List<TicketResponse> getAppealsByPunishment(Server server, String punishmentId) {
        return ticketRepository.findAppealsByPunishmentId(server, punishmentId)
            .stream().map(this::toTicketResponse).toList();
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
            ticket.isHidden(),
            ticket.getReplayUrl(),
            ticket.getAssignedTo()
        );
    }

    public TicketResponse getAppealById(Server server, String appealId) {
        Ticket ticket = ticketRepository.findByTicketId(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL)
            .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));
        return toTicketResponse(ticket);
    }

    public Optional<Ticket> getAppealRaw(Server server, String appealId) {
        return ticketRepository.findByTicketId(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL);
    }

    public TicketResponse toResponse(Ticket appeal) {
        return toTicketResponse(appeal);
    }

    public TicketResponse createAppeal(Server server, CreateAppealRequest request, String presentedToken) {
        String playerUuid = UuidUtils.normalize(request.playerUuid());
        Player player = findPlayerWithPunishment(server, playerUuid, request.punishmentId());
        if (player == null) {
            throw new ResourceNotFoundException("Punishment not found for the specified player");
        }

        player.getPunishments()
            .stream()
            .filter(p -> p.getId().equals(request.punishmentId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Punishment details not found"));

        boolean tokenValid = presentedToken != null && !presentedToken.isBlank()
            && verificationService.validateAppealCreateToken(server, request.punishmentId(), playerUuid, presentedToken);
        if (publicAccessProperties.isAppealTokenRequired() && !tokenValid) {
            throw new ForbiddenException("Appeal verification required");
        }

        Optional<Ticket> activeAppeal = ticketRepository.findAppealsByPunishmentId(server, request.punishmentId())
            .stream()
            .filter(existing -> existing.getAppealWorkflowStatus() == null || !existing.getAppealWorkflowStatus().isTerminal())
            .findFirst();
        if (activeAppeal.isPresent()) {
            if (tokenValid) {
                return toTicketResponse(activeAppeal.get());
            }
            throw new ConflictException("An appeal already exists for this punishment");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("punishmentId", request.punishmentId());
        data.put("playerUuid", playerUuid);
        data.put("contactEmail", request.email());

        if (request.additionalData() != null) {
            Map<String, Object> sanitized = MongoKeyUtils.sanitizeKeys(request.additionalData());
            if (sanitized != null) {
                data.putAll(sanitized);
            }
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
            .attachments(contentService.normalizeAttachments(request.attachments()))
            .build();

        Ticket appeal = Ticket.builder()
            .type(TicketCategory.APPEAL)
            .status(TicketStatus.OPEN)
            .appealWorkflowStatus(AppealWorkflowStatus.OPEN)
            .subject("Appeal for Punishment: " + request.punishmentId())
            .tags(new ArrayList<>())
            .creatorName(username)
            .creatorUuid(playerUuid)
            .notes(new ArrayList<>())
            .replies(new ArrayList<>(List.of(initialReply)))
            .data(data)
            .locked(false)
            .created(new Date())
            .updatedAt(new Date())
            .build();

        Ticket savedAppeal = ticketIdGenerator.insertWithUniqueId(server, "APPEAL", appeal);

        linkAppealToPunishment(server, playerUuid, request.punishmentId(), savedAppeal.getId());

        return toTicketResponse(savedAppeal);
    }

    private Player findPlayerWithPunishment(Server server, String playerUuid, String punishmentId) {
        return playerRepository.findByMinecraftUuid(server, playerUuid)
            .filter(p -> p.getPunishments() != null && p.getPunishments()
                .stream()
                .anyMatch(pun -> pun.getId().equals(punishmentId)))
            .orElse(null);
    }

    private void linkAppealToPunishment(Server server, String playerUuid, String punishmentId, String appealId) {
        PunishmentNote appealOpenedNote = new PunishmentNote(
            IdGenerator.generateShortId(),
            "opened appeal (#" + appealId + ")",
            new Date(),
            "System",
            null
        );

        punishmentMutationService.linkAppealToPunishment(server, playerUuid, punishmentId, appealId, appealOpenedNote);
    }

    private String buildInitialContent(CreateAppealRequest request) {
        StringBuilder content = new StringBuilder();

        if (request.reason() != null && !request.reason().isBlank()) {
            content.append("Appeal Reason: ").append(request.reason()).append("\n");
        }

        if (request.evidence() != null && !request.evidence().isBlank()) {
            content.append("Evidence: ").append(request.evidence()).append("\n");
        }

        String body = content.toString().trim();
        if (body.isEmpty()) {
            return "Appeal submitted for punishment " + request.punishmentId() + ".";
        }
        return body;
    }

    public TicketReply addReply(Server server, String appealId, AddAppealReplyRequest request) {
        Ticket appeal = ticketRepository.findByTicketId(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL)
            .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        if (appeal.isLocked()) {
            throw new ConflictException("Appeal is locked and cannot accept new replies");
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

        return newReply;
    }

    public TicketReply addPublicReply(Server server, String appealId, String content, List<Object> attachments) {
        Ticket appeal = ticketRepository.findByTicketId(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL)
            .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        if (appeal.isLocked()) {
            throw new ConflictException("Appeal is locked and cannot accept new replies");
        }

        TicketReply newReply = TicketReply.builder()
            .id(UUID.randomUUID().toString())
            .name(appeal.getCreatorName())
            .content(content)
            .type("user")
            .created(new Date())
            .staff(false)
            .attachments(contentService.normalizeAttachments(attachments))
            .build();

        ticketRepository.pushReply(server, appealId, newReply);

        return newReply;
    }

    public TicketResponse updateStatus(Server server, String appealId, UpdateAppealStatusRequest request) {
        Ticket appeal = ticketRepository.findByTicketId(server, appealId)
            .filter(t -> t.getType() == TicketCategory.APPEAL)
            .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        List<TicketReply> systemReplies = new ArrayList<>();
        boolean statusChanged = false;

        AppealWorkflowStatus requestedWorkflowStatus = null;
        if (request.status() != null && !request.status().isBlank()) {
            requestedWorkflowStatus = AppealWorkflowStatus.fromCanonicalId(request.status());
        }

        workflowTransitionService.requireReopenBeforeChangingTerminalStatus(appeal.getAppealWorkflowStatus(), requestedWorkflowStatus);

        if (requestedWorkflowStatus != null && requestedWorkflowStatus != appeal.getAppealWorkflowStatus()) {
            TicketStatus lifecycleStatus = requestedWorkflowStatus.toTicketStatus();
            appeal.setAppealWorkflowStatus(requestedWorkflowStatus);
            appeal.setStatus(lifecycleStatus);
            appeal.setLocked(lifecycleStatus.isTerminal());
            statusChanged = true;

            systemReplies.add(workflowTransitionService.statusChangeReply(request.staffUsername(), requestedWorkflowStatus));
        }

        if (request.resolution() != null) {
            Map<String, Object> data = appeal.getData() != null ? new HashMap<>(appeal.getData()) : new HashMap<>();
            if (!request.resolution().equals(data.get("resolution"))) {
                data.put("resolution", request.resolution());
                appeal.setData(data);

                systemReplies.add(createSystemReply(
                    request.staffUsername(),
                    "Resolution set to " + request.resolution() + ".",
                    "RESOLUTION_" + request.resolution().toUpperCase().replace(" ", "_")
                ));
            }
        }

        if (request.locked() != null && request.locked() != appeal.isLocked()) {
            appeal.setLocked(request.locked());

            systemReplies.add(createSystemReply(
                request.staffUsername(),
                "Ticket " + (request.locked() ? "locked" : "unlocked") + ".",
                request.locked() ? "LOCKED" : "UNLOCKED"
            ));
        }

        for (TicketReply reply : systemReplies) {
            appeal.getReplies().add(reply);
        }

        ticketRepository.updateAppealState(
            server,
            appealId,
            statusChanged ? appeal.getAppealWorkflowStatus() : null,
            appeal.getStatus(),
            appeal.isLocked(),
            appeal.getData(),
            systemReplies.isEmpty() ? null : systemReplies
        );

        if (statusChanged && requestedWorkflowStatus != null && requestedWorkflowStatus.isTerminal()) {
            if (requestedWorkflowStatus == AppealWorkflowStatus.APPROVED) {
                pardonPunishment(server, appeal, request.staffUsername());
            } else if (requestedWorkflowStatus == AppealWorkflowStatus.REJECTED) {
                addAppealRejectedNote(server, appeal, request.staffUsername());
            }
        }

        return toTicketResponse(appeal);
    }

    private void addAppealRejectedNote(Server server, Ticket appeal, String staffUsername) {
        Map<String, Object> data = appeal.getData();
        if (data == null) {
            return;
        }

        String punishmentId = (String) data.get("punishmentId");
        String playerUuid = UuidUtils.normalize((String) data.get("playerUuid"));

        if (punishmentId == null || playerUuid == null) {
            return;
        }

        Date now = new Date();
        String staffName = staffUsername != null ? staffUsername : "System";

        PunishmentNote appealRejectedNote = new PunishmentNote(
            IdGenerator.generateShortId(),
            "rejected appeal (#" + appeal.getId() + ")",
            now,
            staffName,
            null
        );

        punishmentMutationService.recordAppealRejection(server, playerUuid, punishmentId, appealRejectedNote, appeal.getId());
    }

    private void pardonPunishment(Server server, Ticket appeal, String staffUsername) {
        Map<String, Object> data = appeal.getData();
        if (data == null) {
            return;
        }

        String punishmentId = (String) data.get("punishmentId");
        String playerUuid = UuidUtils.normalize((String) data.get("playerUuid"));

        if (punishmentId == null || playerUuid == null) {
            return;
        }

        Date now = new Date();
        String staffName = staffUsername != null ? staffUsername : "System";

        PunishmentModification modification = new PunishmentModification(
            IdGenerator.generateShortId(),
            PunishmentModificationType.APPEAL_ACCEPT.name(),
            now,
            staffName,
            null,
            "Appeal approved",
            null,
            appeal.getId(),
            null
        );

        PunishmentNote appealAcceptedNote = new PunishmentNote(
            IdGenerator.generateShortId(),
            "accepted appeal (#" + appeal.getId() + ")",
            now,
            staffName,
            null
        );

        punishmentMutationService.applyAppealApproval(server, playerUuid, punishmentId,
            modification, appealAcceptedNote, "Approved", appeal.getId());

        playerRepository.findByMinecraftUuid(server, playerUuid)
            .ifPresent(player -> {
                Punishment appealedPunishment = player.getPunishments()
                    .stream()
                    .filter(p -> punishmentId.equals(p.getId()))
                    .findFirst()
                    .orElse(null);
                if (appealedPunishment != null && appealedPunishment.data().altBlocking()) {
                    punishmentLifecycleService.cascadePardonLinkedBans(server, punishmentId);
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
}
